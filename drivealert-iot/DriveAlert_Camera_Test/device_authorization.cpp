#include "device_authorization.h"
#include <Preferences.h>
#include <esp_mac.h>
#include <esp_random.h>
#include <mbedtls/md.h>

namespace DeviceAuthorization {
namespace {
constexpr uint32_t SESSION_MAGIC = 0xDAA01701;
constexpr size_t MAX_REVOCATIONS = 32;
struct Revocation { char account[33]; uint32_t through; };
struct Handoff { uint32_t magic; char token[65]; };
// Camera mode follows ESP.restart(), not deep sleep. Initialized RTC data is
// reloaded by startup on this reset path; use no-init just like the outer marker.
RTC_NOINIT_ATTR Handoff handoff;
String ownerAccount, ownerSecret, nonce, sessionToken, deviceIdentity;
String pendingAccount, pendingSecret;
uint32_t pendingSerial = 0;
unsigned long challengeAt = 0;
bool authenticated = false, authenticatedOwner = false, certificateReady = false;
bool storageHealthy = true;
Revocation revoked[MAX_REVOCATIONS] = {};
size_t revokedCount = 0;

bool hex(const String &value, size_t length) {
  if (value.length() != length) return false;
  for (size_t i = 0; i < length; ++i) if (!((value[i] >= '0' && value[i] <= '9') || (value[i] >= 'A' && value[i] <= 'F'))) return false;
  return true;
}
bool equal(const String &a, const String &b) {
  if (a.length() != b.length()) return false;
  uint8_t difference = 0;
  for (size_t i = 0; i < a.length(); ++i) difference |= static_cast<uint8_t>(a[i] ^ b[i]);
  return difference == 0;
}
String randomHex(size_t bytes) {
  uint8_t buffer[32];
  esp_fill_random(buffer, bytes);
  String result; result.reserve(bytes * 2);
  constexpr char digits[] = "0123456789ABCDEF";
  for (size_t i = 0; i < bytes; ++i) { result += digits[buffer[i] >> 4]; result += digits[buffer[i] & 15]; }
  memset(buffer, 0, sizeof(buffer));
  return result;
}
String mac(const String &key, const String &message) {
  if (!hex(key, 64)) return "";
  uint8_t decoded[32], digest[32];
  for (size_t i = 0; i < 32; ++i) {
    char pair[3] = {key[i * 2], key[i * 2 + 1], 0};
    decoded[i] = static_cast<uint8_t>(strtoul(pair, nullptr, 16));
  }
  const int result = mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), decoded, sizeof(decoded),
    reinterpret_cast<const uint8_t *>(message.c_str()), message.length(), digest);
  memset(decoded, 0, sizeof(decoded));
  if (result != 0) return "";
  String output; output.reserve(64);
  constexpr char digits[] = "0123456789ABCDEF";
  for (uint8_t byte : digest) { output += digits[byte >> 4]; output += digits[byte & 15]; }
  memset(digest, 0, sizeof(digest));
  return output;
}
uint32_t revokedThrough(const String &account) {
  for (size_t i = 0; i < revokedCount; ++i) if (account == revoked[i].account) return revoked[i].through;
  return 0;
}
bool serialNumber(const String &value, uint32_t &serial) {
  if (value.isEmpty() || value.length() > 10) return false;
  for (size_t i = 0; i < value.length(); ++i) if (value[i] < '0' || value[i] > '9') return false;
  unsigned long parsed = strtoul(value.c_str(), nullptr, 10);
  if (parsed > 2147483647UL) return false;
  serial = parsed; return true;
}
String challenge() {
  authenticated = false; authenticatedOwner = false; certificateReady = false;
  pendingSecret = ""; pendingAccount = "";
  nonce = randomHex(16); challengeAt = millis();
  return "AUTH_CHALLENGE|" + deviceId() + "|" + nonce + "|" + (ownerAccount.isEmpty() ? "NEW" : "CLAIMED");
}
}

String deviceId() {
  uint8_t address[6];
  if (esp_read_mac(address, ESP_MAC_BT) != ESP_OK) return "";
  char output[13];
  snprintf(output, sizeof(output), "%02X%02X%02X%02X%02X%02X", address[0], address[1], address[2], address[3], address[4], address[5]);
  return String(output);
}

uint32_t pairingPin() {
  Preferences preferences;
  if (!preferences.begin("drivealert_auth", false)) return 0;
  uint32_t pin = preferences.getUInt("pairing_pin", 0);
  if (pin < 100000 || pin > 999999) {
    uint32_t random;
    esp_fill_random(&random, sizeof(random));
    pin = 100000 + random % 900000;
    if (preferences.putUInt("pairing_pin", pin) != sizeof(pin)) pin = 0;
  }
  preferences.end();
  return pin;
}

void begin(bool restoreHandoff) {
  deviceIdentity = deviceId();
  authenticated = false; authenticatedOwner = false; certificateReady = false;
  pendingAccount = ""; pendingSecret = ""; nonce = "";
  ownerAccount = ""; ownerSecret = ""; revokedCount = 0; storageHealthy = true;
  memset(revoked, 0, sizeof(revoked));
  Preferences preferences;
  if (preferences.begin("drivealert_auth", false)) {
    const String record = preferences.getString("owner", "");
    if (record.length() == 97 && record[32] == '|') {
      ownerAccount = record.substring(0, 32); ownerSecret = record.substring(33);
    } else if (!record.isEmpty()) storageHealthy = false;
    const size_t length = preferences.getBytesLength("revocations");
    if (length <= sizeof(revoked) && length % sizeof(Revocation) == 0) {
      revokedCount = length / sizeof(Revocation);
      if (length > 0) preferences.getBytes("revocations", revoked, length);
    } else storageHealthy = false;
    for (size_t i = 0; i < revokedCount; ++i) {
      if (revoked[i].account[32] != 0 || !hex(String(revoked[i].account), 32) || revoked[i].through > 2147483647UL) storageHealthy = false;
    }
    preferences.end();
  } else storageHealthy = false;
  if ((!ownerAccount.isEmpty() || !ownerSecret.isEmpty()) && (!hex(ownerAccount, 32) || !hex(ownerSecret, 64))) storageHealthy = false;
  sessionToken = restoreHandoff && handoff.magic == SESSION_MAGIC && handoff.token[64] == 0 ? String(handoff.token) : "";
  if (!hex(sessionToken, 64)) sessionToken = "";
  memset(&handoff, 0, sizeof(handoff));
}

void newBleConnection() { authenticated = false; authenticatedOwner = false; certificateReady = false; nonce = ""; sessionToken = ""; }
void endBleConnection() { authenticated = false; authenticatedOwner = false; certificateReady = false; pendingSecret = ""; nonce = ""; }
bool bleAuthorized() { return authenticated; }
void preserveHandoff() {
  memset(&handoff, 0, sizeof(handoff));
  if (hex(sessionToken, 64)) { sessionToken.toCharArray(handoff.token, sizeof(handoff.token)); handoff.magic = SESSION_MAGIC; }
}

String command(const String &value) {
  if (!storageHealthy) return "AUTH_ERROR|STORAGE";
  if (value == "AUTH_HELLO") return challenge();
  if (value.startsWith("REVOKE|")) {
    if (!authenticated || !authenticatedOwner || value.length() < 41 || value[39] != '|') return "AUTH_ERROR|OWNER_REQUIRED";
    const String account = value.substring(7, 39);
    uint32_t through = 0;
    if (!hex(account, 32) || account == ownerAccount || !serialNumber(value.substring(40), through) || through == 0) return "AUTH_ERROR|INVALID_REVOCATION";
    size_t index = 0;
    while (index < revokedCount && account != revoked[index].account) ++index;
    if (index == MAX_REVOCATIONS) return "AUTH_ERROR|REVOCATION_CAPACITY";
    if (index < revokedCount && revoked[index].through >= through) return "AUTH_REVOKED";
    Revocation updated[MAX_REVOCATIONS];
    memcpy(updated, revoked, sizeof(updated));
    account.toCharArray(updated[index].account, sizeof(updated[index].account));
    updated[index].through = through;
    const size_t count = max(revokedCount, index + 1);
    Preferences preferences;
    if (!preferences.begin("drivealert_auth", false)) return "AUTH_ERROR|STORAGE";
    const size_t length = count * sizeof(Revocation);
    const bool saved = preferences.putBytes("revocations", updated, length) == length;
    preferences.end();
    if (!saved) return "AUTH_ERROR|STORAGE";
    memcpy(revoked, updated, sizeof(revoked)); revokedCount = count;
    return "AUTH_REVOKED";
  }
  if (value.startsWith("CLAIM|")) {
    if (!ownerAccount.isEmpty() || value.length() != 103 || value[38] != '|') return "AUTH_ERROR|CLAIM_REJECTED";
    const String account = value.substring(6, 38), secret = value.substring(39);
    if (!hex(account, 32) || !hex(secret, 64)) return "AUTH_ERROR|INVALID_CREDENTIAL";
    Preferences preferences;
    if (!preferences.begin("drivealert_auth", false)) return "AUTH_ERROR|STORAGE";
    const String record = account + "|" + secret;
    const bool saved = preferences.putString("owner", record) == record.length();
    preferences.end();
    if (!saved) return "AUTH_ERROR|STORAGE";
    ownerAccount = account; ownerSecret = secret;
    return challenge();
  }
  if (nonce.isEmpty() || millis() - challengeAt > 60000) return "AUTH_ERROR|CHALLENGE_EXPIRED";
  if (value.startsWith("CERT1|")) {
    if (authenticated || value.length() < 104 || value.length() > 120) return "AUTH_ERROR|INVALID_CERTIFICATE";
    const int separator = value.indexOf('|', 39);
    const String account = value.substring(6, 38);
    uint32_t serial = 0;
    if (value[38] != '|' || separator < 40 || !hex(account, 32) || !serialNumber(value.substring(39, separator), serial) || serial == 0 ||
        !hex(value.substring(separator + 1), 64)) return "AUTH_ERROR|INVALID_CERTIFICATE";
    pendingAccount = account; pendingSerial = serial; pendingSecret = value.substring(separator + 1); certificateReady = false;
    return "AUTH_CERT_PART";
  }
  if (value.startsWith("CERT2|")) {
    const String signature = value.substring(6);
    if (ownerSecret.isEmpty() || pendingAccount.isEmpty() || !hex(signature, 64) || pendingAccount == ownerAccount ||
        pendingSerial <= revokedThrough(pendingAccount) || !equal(signature, mac(ownerSecret,
          "CERT|" + deviceId() + "|" + pendingAccount + "|" + String(pendingSerial) + "|" + pendingSecret))) {
      pendingSecret = ""; certificateReady = false;
      return "AUTH_ERROR|CERTIFICATE_REJECTED";
    }
    certificateReady = true;
    return "AUTH_CERT_READY";
  }
  if (value.startsWith("AUTH|")) {
    if (authenticated || value.length() != 102 || value[37] != '|') return "AUTH_ERROR|INVALID_PROOF";
    const String account = value.substring(5, 37), proof = value.substring(38);
    const bool isOwner = account == ownerAccount;
    const String key = isOwner ? ownerSecret : (certificateReady && account == pendingAccount ? pendingSecret : "");
    const String expected = mac(key, "AUTH|" + deviceId() + "|" + account + "|" + nonce);
    // Consume the challenge even on failure: a captured proof cannot be replayed.
    nonce = "";
    if (expected.isEmpty() || !hex(proof, 64) || !equal(expected, proof)) { pendingSecret = ""; return "AUTH_ERROR|PROOF_REJECTED"; }
    authenticated = true; authenticatedOwner = isOwner; pendingSecret = "";
    sessionToken = randomHex(32);
    return "AUTH_OK|" + sessionToken;
  }
  // Owner operations do not use a stale challenge; authorization is connection-bound.
  return "AUTH_ERROR|UNKNOWN_COMMAND";
}

bool requireHttp(httpd_req_t *req) {
  char header[72] = {};
  const bool valid = storageHealthy && hex(sessionToken, 64) && httpd_req_get_hdr_value_len(req, "Authorization") == 71 &&
    httpd_req_get_hdr_value_str(req, "Authorization", header, sizeof(header)) == ESP_OK &&
    equal(String(header), "Bearer " + sessionToken);
  memset(header, 0, sizeof(header));
  if (valid) {
    httpd_resp_set_hdr(req, "X-DriveAlert-Device", deviceIdentity.c_str());
    httpd_resp_set_hdr(req, "X-DriveAlert-Auth", "1");
    return true;
  }
  httpd_resp_set_status(req, "401 Unauthorized");
  httpd_resp_set_type(req, "application/json");
  httpd_resp_set_hdr(req, "Cache-Control", "no-store");
  httpd_resp_sendstr(req, "{\"error\":\"device_authorization_required\"}");
  return false;
}
}

#include <cassert>
#include <iostream>
#include "../DriveAlert_Camera_Test/device_authorization.h"
#include <Preferences.h>
#include <mbedtls/md.h>
unsigned long testMillis = 100;
const String ownerId = "00000000000000000000000000000000";
const String ownerKey = "ABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABABAB";
const String driverId = "6CFCC864FBBCF4E5AC9C71C71E94BDA3";
const String driverKey = "ACA2D3653C0FB5EDA30E85CC66B791E3CC6530E2D975A1594191FFF4E1B16617";
const String certificate = "0C1C1188443E9A8160E26652D4FA4DFD5098605A9ED75C37260EAB1857E524B1";

String hmac(const String &key, const String &message) {
  uint8_t decoded[32], output[32];
  for (size_t i = 0; i < 32; ++i) { char pair[] = {key[i * 2], key[i * 2 + 1], 0}; decoded[i] = strtoul(pair, nullptr, 16); }
  assert(mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), decoded, 32,
    reinterpret_cast<const uint8_t *>(message.c_str()), message.length(), output) == 0);
  char hex[65]; for (size_t i = 0; i < 32; ++i) snprintf(hex + i * 2, 3, "%02X", output[i]);
  return String(hex);
}
String nonce(const String &challenge) { assert(challenge.startsWith("AUTH_CHALLENGE|AABBCCDDEEFF|")); return challenge.substring(28, 60); }
String proof(const String &id, const String &key, const String &challengeNonce) { return "AUTH|" + id + "|" + hmac(key, "AUTH|AABBCCDDEEFF|" + id + "|" + challengeNonce); }
String authenticateOwner() {
  DeviceAuthorization::newBleConnection();
  auto n = nonce(DeviceAuthorization::command("AUTH_HELLO"));
  auto result = DeviceAuthorization::command(proof(ownerId, ownerKey, n));
  assert(result.startsWith("AUTH_OK|")); return result.substring(8);
}
int main() {
  using namespace DeviceAuthorization;
  begin(false); assert(!bleAuthorized());
  const uint32_t pin = pairingPin(); assert(pin >= 100000 && pin <= 999999); assert(pairingPin() == pin);
  assert(command("AUTH|bad").startsWith("AUTH_ERROR"));
  testStorageFailure = true;
  assert(command("CLAIM|" + ownerId + "|" + ownerKey) == "AUTH_ERROR|STORAGE");
  testStorageFailure = false;
  assert(command("CLAIM|" + ownerId + "|" + ownerKey).startsWith("AUTH_CHALLENGE|"));
  assert(command("CLAIM|" + ownerId + "|" + ownerKey) == "AUTH_ERROR|CLAIM_REJECTED");
  String token = authenticateOwner(); assert(bleAuthorized());
  httpd_req_t request;
  assert(!requireHttp(&request)); assert(request.status == "401 Unauthorized");
  request.authorization = "Bearer " + token; assert(requireHttp(&request));
  preserveHandoff(); begin(true); assert(requireHttp(&request));
  begin(true); assert(!requireHttp(&request)); // Handoff is consumed once.
  token = authenticateOwner(); request.authorization = "Bearer " + token;
  preserveHandoff(); begin(false); assert(!requireHttp(&request)); // Cold/manual reset never restores it.
  begin(false); assert(!requireHttp(&request));

  // Exact cross-language certificate vector; only the driver key can prove possession.
  assert(hmac(ownerKey, "CERT|AABBCCDDEEFF|" + driverId + "|1|" + driverKey) == certificate);
  newBleConnection(); auto n = nonce(command("AUTH_HELLO"));
  assert(command("CERT1|" + driverId + "|1|" + driverKey) == "AUTH_CERT_PART");
  assert(command("CERT2|" + certificate) == "AUTH_CERT_READY");
  auto captured = proof(driverId, driverKey, n);
  assert(command(captured).startsWith("AUTH_OK|"));
  assert(command("REVOKE|" + driverId + "|1") == "AUTH_ERROR|OWNER_REQUIRED");
  assert(command(captured).startsWith("AUTH_ERROR"));
  newBleConnection(); command("AUTH_HELLO");
  assert(command("CERT1|" + driverId + "|1|" + driverKey) == "AUTH_CERT_PART");
  assert(command("CERT2|" + certificate) == "AUTH_CERT_READY");
  assert(command(captured) == "AUTH_ERROR|PROOF_REJECTED");

  authenticateOwner();
  testStorageFailure = true;
  assert(command("REVOKE|" + driverId + "|1") == "AUTH_ERROR|STORAGE");
  testStorageFailure = false;
  assert(command("REVOKE|" + driverId + "|1") == "AUTH_REVOKED");
  begin(false); newBleConnection(); command("AUTH_HELLO");
  assert(command("CERT1|" + driverId + "|1|" + driverKey) == "AUTH_CERT_PART");
  assert(command("CERT2|" + certificate) == "AUTH_ERROR|CERTIFICATE_REJECTED");
  newBleConnection(); command("AUTH_HELLO"); testMillis += 60001;
  assert(command(captured) == "AUTH_ERROR|CHALLENGE_EXPIRED");
  std::cout << "Device authorization: ownership, HMAC vectors, nonce replay, HTTP sessions, reboot and revocation tests passed.\n";
}

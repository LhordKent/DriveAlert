#include "wifi_provisioning.h"

#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLESecurity.h>
#include <Preferences.h>
#include <WiFi.h>
#include <esp_system.h>
#include <esp_wifi.h>

#include "provisioning_protocol.h"

namespace {
constexpr char PREFERENCES_NAMESPACE[] = "drivealert_wifi";
constexpr char SSID_KEY[] = "ssid";
constexpr char PASSWORD_KEY[] = "password";
constexpr char BLE_PIN_KEY[] = "ble_pin";

WifiProvisioningManager *activeManager = nullptr;

class ProvisioningServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *) override {
    if (activeManager != nullptr) activeManager->handleBleConnected();
  }

  void onDisconnect(BLEServer *) override {
    if (activeManager != nullptr) activeManager->handleBleDisconnected();
  }
};

enum class InputKind { SSID, PASSWORD, APPLY };

class ProvisioningWriteCallbacks : public BLECharacteristicCallbacks {
 public:
  explicit ProvisioningWriteCallbacks(InputKind kind) : kind(kind) {}

  void onWrite(BLECharacteristic *characteristic) override {
    if (activeManager == nullptr) return;
    const String value = characteristic->getValue();
    switch (kind) {
      case InputKind::SSID:
        activeManager->handleSsidWrite(value);
        break;
      case InputKind::PASSWORD:
        activeManager->handlePasswordWrite(value);
        characteristic->setValue("");
        break;
      case InputKind::APPLY:
        activeManager->handleApplyWrite(value);
        break;
    }
  }

 private:
  InputKind kind;
};

class ProvisioningSecurityCallbacks : public BLESecurityCallbacks {
 public:
  explicit ProvisioningSecurityCallbacks(uint32_t pin) : pin(pin) {}

  uint32_t onPassKeyRequest() override { return pin; }
  void onPassKeyNotify(uint32_t) override {}
  bool onSecurityRequest() override { return true; }
  bool onConfirmPIN(uint32_t) override {
    // Display-only pairing should use passkey entry, not numeric comparison.
    return false;
  }

 private:
  uint32_t pin;
};
}  // namespace

void WifiProvisioningManager::begin() {
  activeManager = this;
  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  // The ESP-IDF world-safe default only scans channels 1-11. Philippine
  // 2.4 GHz hotspots may automatically select channel 12 or 13.
  wifi_country_t wifiCountry = {};
  wifiCountry.cc[0] = 'P';
  wifiCountry.cc[1] = 'H';
  wifiCountry.cc[2] = ' ';
  wifiCountry.schan = 1;
  wifiCountry.nchan = 13;
  wifiCountry.max_tx_power = 78;
  wifiCountry.policy = WIFI_COUNTRY_POLICY_MANUAL;
  const esp_err_t countryResult = esp_wifi_set_country(&wifiCountry);
  Serial.printf(
    "Wi-Fi channel range configuration: 1-13, result=%d.\n",
    static_cast<int>(countryResult));
  WiFi.setSleep(false);
  // A power cycle always returns to BLE_READY. Runtime reconnect is enabled
  // only after the app explicitly starts Wi-Fi for this boot session.
  WiFi.setAutoReconnect(false);
  WiFi.onEvent(
    [this](WiFiEvent_t, WiFiEventInfo_t info) {
      const uint8_t reason = info.wifi_sta_disconnected.reason;
      lastDisconnectReason = reason;
      disconnectReasonSeen = true;
      Serial.printf(
        "Wi-Fi disconnected: reason=%u (%s).\n",
        reason,
        disconnectReasonName(reason));
    },
    ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
  provisioningPin = loadOrCreateProvisioningPin();
  loadStoredCredentials();
}

bool WifiProvisioningManager::connectUsingStoredCredentials() {
  if (!hasStoredCredentials()) return false;
  Serial.println("Explicit app command accepted; connecting with saved Wi-Fi credentials.");
  WiFi.setAutoReconnect(true);
  return connectToWifi(savedSsid, savedPassword);
}

void WifiProvisioningManager::startProvisioning() {
  if (provisioningActive) return;
  WiFi.disconnect(false, false);
  const String deviceName = provisioningDeviceName();
  BLEDevice::init(deviceName.c_str());

  BLESecurity *security = new BLESecurity();
  security->setPassKey(true, provisioningPin);
  security->setCapability(ESP_IO_CAP_OUT);
  security->setAuthenticationMode(true, true, true);
  security->setKeySize(16);
  BLEDevice::setSecurityCallbacks(new ProvisioningSecurityCallbacks(provisioningPin));

  bleServer = BLEDevice::createServer();
  bleServer->setCallbacks(new ProvisioningServerCallbacks());
  BLEService *service = bleServer->createService(DriveAlertProvisioningProtocol::SERVICE_UUID);

  const uint32_t writeProperties = BLECharacteristic::PROPERTY_WRITE;
  BLECharacteristic *ssidCharacteristic = service->createCharacteristic(
    DriveAlertProvisioningProtocol::SSID_UUID, writeProperties);
  BLECharacteristic *passwordCharacteristic = service->createCharacteristic(
    DriveAlertProvisioningProtocol::PASSWORD_UUID, writeProperties);
  BLECharacteristic *applyCharacteristic = service->createCharacteristic(
    DriveAlertProvisioningProtocol::APPLY_UUID, writeProperties);
  statusCharacteristic = service->createCharacteristic(
    DriveAlertProvisioningProtocol::STATUS_UUID,
    BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);

  const uint16_t writePermissions = ESP_GATT_PERM_WRITE_ENC_MITM;
  ssidCharacteristic->setAccessPermissions(writePermissions);
  passwordCharacteristic->setAccessPermissions(writePermissions);
  applyCharacteristic->setAccessPermissions(writePermissions);
  statusCharacteristic->setAccessPermissions(ESP_GATT_PERM_READ_ENC_MITM);
  statusCharacteristic->addDescriptor(new BLE2902());

  ssidCharacteristic->setCallbacks(new ProvisioningWriteCallbacks(InputKind::SSID));
  passwordCharacteristic->setCallbacks(new ProvisioningWriteCallbacks(InputKind::PASSWORD));
  applyCharacteristic->setCallbacks(new ProvisioningWriteCallbacks(InputKind::APPLY));

  service->start();
  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->addServiceUUID(DriveAlertProvisioningProtocol::SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->setMinPreferred(0x06);
  advertising->setMaxPreferred(0x12);
  BLEDevice::startAdvertising();

  provisioningActive = true;
  publishStatus(DriveAlertProvisioningProtocol::STATUS_READY);
  Serial.printf("BLE provisioning active as %s. Pairing PIN: %06u\n", deviceName.c_str(), provisioningPin);
  Serial.println("The PIN protects setup credentials; the Wi-Fi password is never printed.");
}

void WifiProvisioningManager::loop() {
  if (!provisioningActive) return;
  if (applyRequested) {
    applyRequested = false;
    processCandidateCredentials();
  }
  if (connectSavedRequested) {
    connectSavedRequested = false;
    processStoredCredentials();
  }
  if (credentialStatusRequested) {
    credentialStatusRequested = false;
    publishCredentialStatus();
  }
  if (stopBleAtMs != 0 && static_cast<long>(millis() - stopBleAtMs) >= 0) {
    stopBleAtMs = 0;
    provisioningSuccessReady = true;
  }
}

bool WifiProvisioningManager::takeProvisioningSuccess() {
  if (!provisioningSuccessReady) return false;
  provisioningSuccessReady = false;
  return true;
}

bool WifiProvisioningManager::hasStoredCredentials() {
  return credentialsAreStructurallyValid(savedSsid, savedPassword);
}

void WifiProvisioningManager::clearWifiCredentials() {
  Preferences preferences;
  if (preferences.begin(PREFERENCES_NAMESPACE, false)) {
    preferences.remove(SSID_KEY);
    preferences.remove(PASSWORD_KEY);
    preferences.end();
  }
  savedSsid = "";
  savedPassword = "";
  Serial.println("Stored Wi-Fi credentials cleared.");
}

void WifiProvisioningManager::handleSsidWrite(const String &value) {
  candidateSsid = value;
  publishStatus(DriveAlertProvisioningProtocol::STATUS_RECEIVED);
}

void WifiProvisioningManager::handlePasswordWrite(const String &value) {
  candidatePassword = value;
  publishStatus(DriveAlertProvisioningProtocol::STATUS_RECEIVED);
}

void WifiProvisioningManager::handleApplyWrite(const String &value) {
  if (value == DriveAlertProvisioningProtocol::APPLY_COMMAND) {
    applyRequested = true;
  } else if (value == DriveAlertProvisioningProtocol::CONNECT_SAVED_COMMAND) {
    connectSavedRequested = true;
  } else if (value == DriveAlertProvisioningProtocol::QUERY_CREDENTIALS_COMMAND) {
    credentialStatusRequested = true;
  }
}

void WifiProvisioningManager::handleBleConnected() {
  bleClientConnected = true;
}

void WifiProvisioningManager::handleBleDisconnected() {
  bleClientConnected = false;
  if (provisioningActive && stopBleAtMs == 0) BLEDevice::startAdvertising();
}

void WifiProvisioningManager::loadStoredCredentials() {
  Preferences preferences;
  if (!preferences.begin(PREFERENCES_NAMESPACE, true)) return;
  savedSsid = preferences.getString(SSID_KEY, "");
  savedPassword = preferences.getString(PASSWORD_KEY, "");
  preferences.end();
}

bool WifiProvisioningManager::connectToWifi(const String &ssid, const String &password) {
  Serial.printf(
    "Wi-Fi attempt starting: SSID='%s', SSID bytes=%u, security input=%s.\n",
    ssid.c_str(),
    static_cast<unsigned>(ssid.length()),
    password.isEmpty() ? "open" : "password-protected");

  const int networkCount = WiFi.scanNetworks(false, true);
  bool targetFound = false;
  if (networkCount < 0) {
    Serial.printf("Pre-connect Wi-Fi scan failed: result=%d.\n", networkCount);
  } else {
    for (int index = 0; index < networkCount; ++index) {
      if (WiFi.SSID(index) != ssid) continue;
      targetFound = true;
      Serial.printf(
        "Target network visible: YES, RSSI=%d dBm, channel=%d, auth-mode=%d.\n",
        WiFi.RSSI(index),
        WiFi.channel(index),
        static_cast<int>(WiFi.encryptionType(index)));
      break;
    }
    if (!targetFound) {
      Serial.printf("Target network visible: NO (scan saw %d networks).\n", networkCount);
    }
  }
  WiFi.scanDelete();

  WiFi.disconnect(false, false);
  delay(100);
  lastDisconnectReason = 0;
  disconnectReasonSeen = false;
  const wl_status_t beginStatus = WiFi.begin(ssid.c_str(), password.c_str());
  Serial.printf(
    "WiFi.begin returned %u (%s); waiting up to %lu ms.\n",
    static_cast<unsigned>(beginStatus),
    wifiStatusName(beginStatus),
    CONNECTION_TIMEOUT_MS);

  const unsigned long startedAt = millis();
  wl_status_t previousStatus = beginStatus;
  while (millis() - startedAt < CONNECTION_TIMEOUT_MS) {
    const wl_status_t currentStatus = WiFi.status();
    if (currentStatus == WL_CONNECTED) {
      Serial.printf(
        "Wi-Fi connected: IP=%s, RSSI=%d dBm, channel=%d.\n",
        WiFi.localIP().toString().c_str(),
        WiFi.RSSI(),
        WiFi.channel());
      return true;
    }
    if (currentStatus != previousStatus) {
      Serial.printf(
        "Wi-Fi status changed: %u (%s).\n",
        static_cast<unsigned>(currentStatus),
        wifiStatusName(currentStatus));
      previousStatus = currentStatus;
    }
    delay(250);
  }

  const wl_status_t finalStatus = WiFi.status();
  if (disconnectReasonSeen) {
    const uint8_t reason = lastDisconnectReason;
    Serial.printf(
      "Wi-Fi attempt timed out: status=%u (%s), last-disconnect=%u (%s).\n",
      static_cast<unsigned>(finalStatus),
      wifiStatusName(finalStatus),
      reason,
      disconnectReasonName(reason));
  } else {
    Serial.printf(
      "Wi-Fi attempt timed out: status=%u (%s), no disconnect reason received.\n",
      static_cast<unsigned>(finalStatus),
      wifiStatusName(finalStatus));
  }
  return false;
}

bool WifiProvisioningManager::storeCredentials(const String &ssid, const String &password) {
  Preferences preferences;
  if (!preferences.begin(PREFERENCES_NAMESPACE, false)) return false;
  const size_t ssidBytes = preferences.putString(SSID_KEY, ssid);
  const size_t passwordBytes = preferences.putString(PASSWORD_KEY, password);
  preferences.end();
  if (ssidBytes == 0 || (password.length() > 0 && passwordBytes == 0)) return false;
  savedSsid = ssid;
  savedPassword = password;
  return true;
}

void WifiProvisioningManager::processCandidateCredentials() {
  if (!credentialsAreStructurallyValid(candidateSsid, candidatePassword)) {
    publishStatus(DriveAlertProvisioningProtocol::STATUS_INVALID_CREDENTIALS);
    candidatePassword = "";
    return;
  }

  publishStatus(DriveAlertProvisioningProtocol::STATUS_CONNECTING);
  WiFi.setAutoReconnect(true);
  if (!connectToWifi(candidateSsid, candidatePassword)) {
    WiFi.setAutoReconnect(false);
    publishStatus(DriveAlertProvisioningProtocol::STATUS_CONNECTION_FAILED);
    candidatePassword = "";
    return;
  }

  if (!storeCredentials(candidateSsid, candidatePassword)) {
    publishStatus(DriveAlertProvisioningProtocol::STATUS_ERROR);
    candidatePassword = "";
    return;
  }

  candidatePassword = "";
  publishStatus("CONNECTED|" + WiFi.localIP().toString() + "|" + deviceHostname());
  stopBleAtMs = millis() + BLE_STATUS_GRACE_MS;
}

void WifiProvisioningManager::processStoredCredentials() {
  if (!hasStoredCredentials()) {
    publishStatus(DriveAlertProvisioningProtocol::STATUS_NO_CREDENTIALS);
    return;
  }
  publishStatus(DriveAlertProvisioningProtocol::STATUS_CONNECTING);
  if (!connectUsingStoredCredentials()) {
    WiFi.setAutoReconnect(false);
    publishStatus(DriveAlertProvisioningProtocol::STATUS_CONNECTION_FAILED);
    return;
  }
  publishStatus("CONNECTED|" + WiFi.localIP().toString() + "|" + deviceHostname());
  stopBleAtMs = millis() + BLE_STATUS_GRACE_MS;
}

void WifiProvisioningManager::publishCredentialStatus() {
  if (hasStoredCredentials()) {
    publishStatus(String(DriveAlertProvisioningProtocol::STATUS_SAVED_CREDENTIALS_PREFIX) + savedSsid);
  } else {
    publishStatus(DriveAlertProvisioningProtocol::STATUS_NO_CREDENTIALS);
  }
}

void WifiProvisioningManager::publishStatus(const String &status) {
  if (statusCharacteristic == nullptr) return;
  statusCharacteristic->setValue(status.c_str());
  if (bleClientConnected) statusCharacteristic->notify();
}

uint32_t WifiProvisioningManager::loadOrCreateProvisioningPin() {
  Preferences preferences;
  if (!preferences.begin(PREFERENCES_NAMESPACE, false)) {
    return 100000 + (esp_random() % 900000);
  }
  uint32_t pin = preferences.getUInt(BLE_PIN_KEY, 0);
  if (pin < 100000 || pin > 999999) {
    pin = 100000 + (esp_random() % 900000);
    preferences.putUInt(BLE_PIN_KEY, pin);
  }
  preferences.end();
  return pin;
}

String WifiProvisioningManager::provisioningDeviceName() const {
  const uint16_t suffix = static_cast<uint16_t>(ESP.getEfuseMac() & 0xFFFF);
  char name[20];
  snprintf(name, sizeof(name), "DriveAlert-%04X", suffix);
  return String(name);
}

String WifiProvisioningManager::deviceHostname() const {
  const uint64_t chipId = ESP.getEfuseMac();
  char hostname[24];
  snprintf(hostname, sizeof(hostname), "drivealert-%04x", static_cast<uint16_t>(chipId & 0xFFFF));
  return String(hostname);
}

const char *WifiProvisioningManager::wifiStatusName(uint8_t status) {
  switch (status) {
    case WL_IDLE_STATUS: return "IDLE";
    case WL_NO_SSID_AVAIL: return "NO_SSID_AVAILABLE";
    case WL_SCAN_COMPLETED: return "SCAN_COMPLETED";
    case WL_CONNECTED: return "CONNECTED";
    case WL_CONNECT_FAILED: return "CONNECT_FAILED";
    case WL_CONNECTION_LOST: return "CONNECTION_LOST";
    case WL_DISCONNECTED: return "DISCONNECTED";
    case WL_STOPPED: return "STOPPED";
    case WL_NO_SHIELD: return "NO_SHIELD";
    default: return "UNKNOWN";
  }
}

const char *WifiProvisioningManager::disconnectReasonName(uint8_t reason) {
  // ESP-IDF Wi-Fi reason codes. Keep numeric output as the authoritative value.
  switch (reason) {
    case 2: return "AUTH_EXPIRE";
    case 3: return "AUTH_LEAVE";
    case 4: return "ASSOC_EXPIRE";
    case 15: return "4WAY_HANDSHAKE_TIMEOUT";
    case 23: return "802_1X_AUTH_FAILED";
    case 36: return "STA_LEAVING";
    case 200: return "BEACON_TIMEOUT";
    case 201: return "NO_AP_FOUND";
    case 202: return "AUTH_FAIL";
    case 203: return "ASSOC_FAIL";
    case 204: return "HANDSHAKE_TIMEOUT";
    case 205: return "CONNECTION_FAIL";
    case 210: return "NO_AP_FOUND_SECURITY";
    case 211: return "NO_AP_FOUND_AUTHMODE";
    default: return "OTHER";
  }
}

bool WifiProvisioningManager::credentialsAreStructurallyValid(
  const String &ssid, const String &password) {
  const bool validPassword = password.isEmpty() || (password.length() >= 8 && password.length() <= 63);
  return !ssid.isEmpty() && ssid.length() <= 32 && validPassword;
}

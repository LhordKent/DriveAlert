#ifndef DRIVEALERT_WIFI_PROVISIONING_H
#define DRIVEALERT_WIFI_PROVISIONING_H

#include <Arduino.h>

class BLECharacteristic;
class BLEServer;

class WifiProvisioningManager {
 public:
  static constexpr unsigned long CONNECTION_TIMEOUT_MS = 20000;
  static constexpr unsigned long BLE_STATUS_GRACE_MS = 1500;

  void begin();
  bool connectUsingStoredCredentials();
  void startProvisioning();
  void loop();
  bool takeProvisioningSuccess();
  bool hasStoredCredentials();
  void clearWifiCredentials();
  String deviceHostname() const;

  void handleSsidWrite(const String &value);
  void handlePasswordWrite(const String &value);
  void handleApplyWrite(const String &value);
  void handleBleConnected();
  void handleBleDisconnected();

 private:
  String savedSsid;
  String savedPassword;
  String candidateSsid;
  String candidatePassword;
  BLEServer *bleServer = nullptr;
  BLECharacteristic *statusCharacteristic = nullptr;
  bool provisioningActive = false;
  bool bleClientConnected = false;
  bool applyRequested = false;
  bool connectSavedRequested = false;
  bool credentialStatusRequested = false;
  bool provisioningSuccessReady = false;
  unsigned long stopBleAtMs = 0;
  uint32_t provisioningPin = 0;
  volatile uint8_t lastDisconnectReason = 0;
  volatile bool disconnectReasonSeen = false;

  void loadStoredCredentials();
  bool connectToWifi(const String &ssid, const String &password);
  bool storeCredentials(const String &ssid, const String &password);
  void processCandidateCredentials();
  void processStoredCredentials();
  void publishCredentialStatus();
  void publishStatus(const String &status);
  uint32_t loadOrCreateProvisioningPin();
  String provisioningDeviceName() const;
  static const char *wifiStatusName(uint8_t status);
  static const char *disconnectReasonName(uint8_t reason);
  static bool credentialsAreStructurallyValid(const String &ssid, const String &password);
};

#endif

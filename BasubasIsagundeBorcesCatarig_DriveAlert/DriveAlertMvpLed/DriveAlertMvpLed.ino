#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>

#define WIFI_SSID "BravoFoxtrot"
#define WIFI_PASSWORD "basubasbasubas112233"

constexpr int LED_PIN = 14;
constexpr unsigned long COMMAND_POLL_INTERVAL_MS = 1000;
constexpr unsigned long HEARTBEAT_INTERVAL_MS = 10000;

const char* DEVICE_URL =
    "https://drivealert-lhordkent-default-rtdb.asia-southeast1.firebasedatabase.app/devices/esp32_01";

unsigned long lastCommandPollAt = 0;
unsigned long lastHeartbeatAt = 0;
bool physicalLedState = false;
bool hasReportedLedState = false;

int firebaseRequest(
    const String& method,
    const String& childPath,
    const String& jsonBody,
    String* responseBody = nullptr) {
  WiFiClientSecure secureClient;
  secureClient.setInsecure();
  secureClient.setHandshakeTimeout(15);

  HTTPClient https;
  const String url = String(DEVICE_URL) + childPath + ".json";
  https.setConnectTimeout(15000);
  https.setTimeout(15000);

  if (!https.begin(secureClient, url)) {
    Serial.println("HTTPS setup failed.");
    return -1;
  }

  int statusCode;
  if (method == "GET") {
    statusCode = https.GET();
  } else {
    https.addHeader("Content-Type", "application/json");
    statusCode = https.PUT(jsonBody);
  }

  if (responseBody != nullptr && statusCode > 0) {
    *responseBody = https.getString();
  }
  if (statusCode <= 0) {
    Serial.printf("HTTPS %s failed: %s\n", method.c_str(), HTTPClient::errorToString(statusCode).c_str());
  } else if (statusCode < 200 || statusCode >= 300) {
    Serial.printf("Firebase %s returned HTTP %d: %s\n", method.c_str(), statusCode, https.getString().c_str());
  }

  https.end();
  return statusCode;
}

bool putValue(const String& childPath, const String& jsonValue) {
  const int statusCode = firebaseRequest("PUT", childPath, jsonValue);
  return statusCode >= 200 && statusCode < 300;
}

bool reportHeartbeat() {
  const bool statusWritten = putValue("/status", "\"online\"");
  const bool timestampWritten = putValue("/lastUpdate", "{\".sv\":\"timestamp\"}");
  if (!statusWritten || !timestampWritten) {
    Serial.println("Heartbeat report failed. Check the published RTDB rules.");
  }
  return statusWritten && timestampWritten;
}

void reportLedState() {
  const bool stateWritten = putValue("/actualLedState", physicalLedState ? "true" : "false");
  const bool heartbeatWritten = reportHeartbeat();
  if (stateWritten && heartbeatWritten) {
    hasReportedLedState = true;
    Serial.printf(
        "Physical LED is %s; actual state reported to Firebase.\n",
        physicalLedState ? "ON" : "OFF");
  }
}

void applyCommand(const String& command) {
  const bool requestedState = command == "ON";
  if (command != "ON" && command != "OFF") return;

  if (physicalLedState != requestedState || !hasReportedLedState) {
    physicalLedState = requestedState;
    digitalWrite(LED_PIN, physicalLedState ? HIGH : LOW);
    reportLedState();
  }
}

void pollCommand() {
  String response;
  const int statusCode = firebaseRequest("GET", "/command", "", &response);
  if (statusCode < 200 || statusCode >= 300) return;

  response.trim();
  if (response == "\"ON\"") {
    applyCommand("ON");
  } else if (response == "\"OFF\"") {
    applyCommand("OFF");
  } else {
    Serial.printf("Ignored invalid command payload: %s\n", response.c_str());
  }
}

void connectWifi() {
  Serial.printf("Connecting to Wi-Fi: %s", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(300);
  }
  Serial.printf("\nWi-Fi connected. IP: %s\n", WiFi.localIP().toString().c_str());
  Serial.printf("Free heap: %u bytes\n", ESP.getFreeHeap());
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  connectWifi();
  Serial.println("Firebase REST client started.");
  pollCommand();
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    connectWifi();
  }

  const unsigned long now = millis();
  if (now - lastCommandPollAt >= COMMAND_POLL_INTERVAL_MS) {
    lastCommandPollAt = now;
    pollCommand();
  }

  if (now - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
    lastHeartbeatAt = now;
    reportHeartbeat();
  }

  delay(20);
}

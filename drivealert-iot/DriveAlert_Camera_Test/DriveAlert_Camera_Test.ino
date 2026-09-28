#include <Arduino.h>
#include "esp_camera.h"
#include <WiFi.h>
#include <ESPmDNS.h>
#include <esp_attr.h>
#include <esp_system.h>

#include "board_config.h"
#include "warning_output.h"
#include "wifi_provisioning.h"

bool startCameraServer();
void setupLedFlash();

RTC_NOINIT_ATTR uint32_t cameraHandoffAuthorization;

namespace {
constexpr unsigned long WIFI_RECONNECT_INTERVAL_MS = 5000;
constexpr unsigned long WIFI_RECONFIGURE_AFTER_MS = 90000;
constexpr uint32_t CAMERA_HANDOFF_MAGIC = 0xDA17CA4E;

WifiProvisioningManager wifiProvisioning;
bool normalModeActive = false;
volatile bool manualDisconnectRequested = false;
bool mdnsActive = false;
bool mdnsAttemptedForConnection = false;
unsigned long wifiLostAtMs = 0;
unsigned long lastReconnectAttemptMs = 0;







void startMdnsOnceForConnection() {
  if (mdnsActive || mdnsAttemptedForConnection) return;
  mdnsAttemptedForConnection = true;
  const String hostname = wifiProvisioning.deviceHostname();
  mdnsActive = MDNS.begin(hostname.c_str());
  if (mdnsActive) {
    MDNS.addService("drivealert", "tcp", 81);
    MDNS.addServiceTxt("drivealert", "tcp", "path", "/stream");
    MDNS.addServiceTxt("drivealert", "tcp", "controlPort", "80");
    Serial.printf("DriveAlert discovery: %s.local (_drivealert._tcp).\n", hostname.c_str());
  } else {
    Serial.println("DriveAlert mDNS unavailable for this connection; using the numeric IP without retrying mDNS.");
  }
}

bool initializeCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.frame_size = FRAMESIZE_UXGA;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 12;
  config.fb_count = 1;

  if (config.pixel_format == PIXFORMAT_JPEG) {
    if (psramFound()) {
      config.jpeg_quality = 10;
      config.fb_count = 2;
      config.grab_mode = CAMERA_GRAB_LATEST;
    } else {
      config.frame_size = FRAMESIZE_SVGA;
      config.fb_location = CAMERA_FB_IN_DRAM;
    }
  } else {
    config.frame_size = FRAMESIZE_240X240;
#if CONFIG_IDF_TARGET_ESP32S3
    config.fb_count = 2;
#endif
  }

#if defined(CAMERA_MODEL_ESP_EYE)
  pinMode(13, INPUT_PULLUP);
  pinMode(14, INPUT_PULLUP);
#endif

  const esp_err_t error = esp_camera_init(&config);
  if (error != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x\n", error);
    return false;
  }

  sensor_t *sensor = esp_camera_sensor_get();
  if (sensor->id.PID == OV3660_PID) {
    sensor->set_vflip(sensor, 1);
    sensor->set_brightness(sensor, 1);
    sensor->set_saturation(sensor, -2);
  }
  if (config.pixel_format == PIXFORMAT_JPEG) sensor->set_framesize(sensor, FRAMESIZE_QVGA);

#if defined(CAMERA_MODEL_M5STACK_WIDE) || defined(CAMERA_MODEL_M5STACK_ESP32CAM)
  sensor->set_vflip(sensor, 1);
  sensor->set_hmirror(sensor, 1);
#endif

#if defined(CAMERA_MODEL_ESP32S3_EYE)
  sensor->set_vflip(sensor, 1);
#endif

#if defined(LED_GPIO_NUM)
  setupLedFlash();
#endif
  return true;
}

void startNormalMode() {
  if (normalModeActive) return;
  if (!initializeCamera()) {
    Serial.println("Normal mode could not start because camera initialization failed.");
    return;
  }
  if (!startCameraServer()) {
    Serial.println("MJPEG server failed to start; rebooting to BLE-ready mode.");
    esp_camera_deinit();
    delay(100);
    ESP.restart();
    return;
  }
  startMdnsOnceForConnection();
  normalModeActive = true;
  wifiLostAtMs = 0;
  Serial.print("Camera Ready! Control UI: http://");
  Serial.println(WiFi.localIP());
  Serial.print("MJPEG stream: http://");
  Serial.print(WiFi.localIP());
  Serial.println(":81/stream");
}

void maintainWifiConnection() {
  if (WiFi.status() == WL_CONNECTED) {
    wifiLostAtMs = 0;
    startMdnsOnceForConnection();
    return;
  }
  if (mdnsActive) {
    MDNS.end();
    mdnsActive = false;
    // Permit one fresh mDNS start after an actual Wi-Fi reconnection.
    mdnsAttemptedForConnection = false;
  }
  const unsigned long now = millis();
  if (wifiLostAtMs == 0) wifiLostAtMs = now;
  if (now - lastReconnectAttemptMs >= WIFI_RECONNECT_INTERVAL_MS) {
    lastReconnectAttemptMs = now;
    Serial.println("Wi-Fi unavailable; retrying saved network.");
    WiFi.reconnect();
  }
  if (now - wifiLostAtMs >= WIFI_RECONFIGURE_AFTER_MS) {
    Serial.println("Wi-Fi recovery window expired; rebooting into bounded startup/provisioning flow.");
    delay(100);
    ESP.restart();
  }
}
}  // namespace

// Called by the HTTP control task after its response has been sent. The Arduino
// loop performs the restart so the request handler never tears down its own server.
void requestManualDisconnect() {
  manualDisconnectRequested = true;
}

void setup() {
  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Serial.println();
  WarningOutput::begin();
  const bool appAuthorizedCameraHandoff =
    esp_reset_reason() == ESP_RST_SW && cameraHandoffAuthorization == CAMERA_HANDOFF_MAGIC;
  cameraHandoffAuthorization = 0;
  wifiProvisioning.begin();

  if (appAuthorizedCameraHandoff) {
    Serial.println("App-authorized handoff restart; connecting Wi-Fi without starting BLE.");
    if (wifiProvisioning.connectUsingStoredCredentials()) {
      startNormalMode();
      return;
    }
    Serial.println("App-authorized Wi-Fi handoff failed; returning to BLE-ready mode.");
  }
  Serial.println("Manual-connect boot: Wi-Fi remains off until the Android app explicitly requests it.");
  wifiProvisioning.startProvisioning();
}

// Safe maintenance API. No GPIO is assigned until a non-conflicting physical control is validated.
void clearWifiCredentials() {
  wifiProvisioning.clearWifiCredentials();
}

void loop() {
  WarningOutput::loop();
  wifiProvisioning.loop();
  if (manualDisconnectRequested) {
    normalModeActive = false;
    cameraHandoffAuthorization = 0;
    WiFi.setAutoReconnect(false);
    Serial.println("Explicit app disconnect accepted; restarting into BLE-ready mode with saved credentials retained.");
    delay(250);
    ESP.restart();
  }
  if (wifiProvisioning.takeProvisioningSuccess()) {
    // A controlled software restart cleanly releases all BLE resources before
    // camera/HTTP allocation. The RTC marker is accepted only for this explicit
    // app-authorized handoff and is consumed immediately on the next boot.
    cameraHandoffAuthorization = CAMERA_HANDOFF_MAGIC;
    Serial.println("Wi-Fi validated; restarting once into app-authorized camera mode.");
    delay(100);
    ESP.restart();
  }
  if (normalModeActive) maintainWifiConnection();
  delay(20);
}

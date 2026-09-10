# DriveAlert IoT camera firmware

## Confirmed development hardware

- ESP32-CAM-compatible board with ESP-32S/classic ESP32 module
- Approximately 4 MB PSRAM
- AI-Thinker-compatible camera GPIO mapping (`CAMERA_MODEL_AI_THINKER`)
- OV3660 IR-capable camera sensor
- ESP32-CAM-MB used for USB programming, power, and serial monitoring

This is not an ESP32-S3/Freenove board and the sensor is not OV2640. The CameraWebServer-derived source retains support code for other sensors, but the DriveAlert development unit is OV3660. The existing AI-Thinker pin map and OV3660-specific flip/brightness/saturation adjustments remain unchanged.

## Project layout

- `DriveAlert_Camera_Test/DriveAlert_Camera_Test.ino`: startup, camera initialization, normal-mode recovery
- `DriveAlert_Camera_Test/wifi_provisioning.*`: Preferences and secure BLE provisioning
- `DriveAlert_Camera_Test/provisioning_protocol.h`: firmware UUID/status constants
- `DriveAlert_Camera_Test/app_httpd.cpp`: unchanged working control and MJPEG servers
- `PROVISIONING_PROTOCOL.md`: shared Android/firmware GATT contract

## Camera and HTTP behavior

The established stream implementation is preserved:

- Control/UI server: port 80
- JPEG still: `/capture`
- BMP still: `/bmp`
- MJPEG stream: `http://<ESP32-DHCP-IP>:81/stream`
- MIME: `multipart/x-mixed-replace;boundary=123456789000000000000987654321`

Frames come from `esp_camera_fb_get()`. Native JPEG buffers are transmitted directly and returned with `esp_camera_fb_return()`. The stream loop and fallback JPEG conversion remain unchanged.

Camera configuration remains JPEG at 20 MHz XCLK, initially UXGA, then QVGA after initialization. With PSRAM, the firmware uses JPEG quality 10, two framebuffers, and `CAMERA_GRAB_LATEST`. The AI-Thinker pin mapping is PWDN 32, XCLK 0, SDA 26, SCL 27, Y9..Y2 35/34/39/36/21/19/18/5, VSYNC 25, HREF 23, PCLK 22, and LED 4.

## Wi-Fi provisioning

There are no hardcoded SSID/password values in the consolidated firmware.

Preferences namespace and keys:

- Namespace: `drivealert_wifi`
- Active SSID: `ssid`
- Active password: `password`
- Device-specific BLE pairing PIN: `ble_pin`

Every boot enters BLE provisioning mode without initializing the camera or HTTP servers. Stored credentials are loaded only so Android can query whether a network is available for an explicit `CONNECT_SAVED` command. The firmware never calls `WiFi.begin()` at boot merely because NVS credentials exist.

Candidate credentials received over BLE are tested first. They replace stored credentials only after the ESP32 joins the network successfully. Failure leaves any previous known-good Preferences values unchanged and keeps BLE provisioning available.

After the user explicitly chooses Connect and Wi-Fi succeeds, Android receives `CONNECTED|<local-ip>|<hostname>`. Firmware allows 1.5 seconds for delivery, then performs one app-authorized software restart with an RTC-memory marker. The marker is accepted only after that software restart and is immediately consumed; this second boot connects the already-validated credentials without initializing BLE, leaving enough internal RAM and socket resources for the OV3660 and HTTP/MJPEG servers. Normal power-ons and runtime-recovery reboots have no marker and always return to BLE-ready mode. Older Android builds remain compatible with the original IP-only response.

mDNS startup is attempted once per Wi-Fi connection. If the retained BLE stack leaves insufficient internal RAM for the mDNS task, firmware keeps the numeric DHCP IP and does not repeatedly retry or flood the serial console. Android already receives that current IP in the BLE success response.

### BLE security

The ESP32 uses the bundled Bluedroid implementation with:

- LE Secure Connections
- MITM authentication
- Bonding
- 16-byte encryption key
- `ESP_GATT_PERM_WRITE_ENC_MITM` on credential/command characteristics
- `ESP_GATT_PERM_READ_ENC_MITM` on status

A random six-digit pairing PIN is generated on first boot, stored under `ble_pin`, and printed to the 115200-baud development serial console. Android uses its system pairing dialog. The Wi-Fi password is never printed, echoed in status, or included in the advertised name.

The advertised name is `DriveAlert-XXXX`, where `XXXX` is a non-secret suffix derived from the device eFuse MAC.

## Runtime recovery

- Automatic reconnect is disabled at boot and enabled only after an explicit `APPLY` or `CONNECT_SAVED` command for the current power cycle.
- During normal mode, explicit reconnect is requested every 5 seconds while disconnected.
- Credentials are not erased during transient loss.
- After 90 seconds without Wi-Fi, the ESP32 reboots.
- The next boot returns to BLE-ready mode and waits for another explicit Android command, even though credentials remain stored.

Rebooting avoids keeping BLE, camera, Wi-Fi, and the HTTP stream allocated together. These timings are engineering constants, not detection rules.

`WifiProvisioningManager::clearWifiCredentials()` safely removes only `ssid` and `password`. No physical GPIO trigger is assigned because available controls have not been validated against the camera wiring.

## Build verification

Required toolchain:

- Arduino IDE/CLI
- Espressif Arduino-ESP32 core 3.3.11
- Board: AI Thinker ESP32-CAM (`esp32:esp32:esp32cam`)
- PSRAM enabled on the physical board

The firmware compiles for the actual classic ESP32-CAM target with Preferences, BLE security, mDNS, camera code, and HTTP/MJPEG server included. Compilation is verified; the new provisioning response and rediscovery behavior still require the manual hardware tests below.

## Manual hardware tests

### A — Fresh device

1. Invoke `clearWifiCredentials()` temporarily from a controlled maintenance build or erase NVS with the programming tool.
2. Reboot and open Serial Monitor at 115200 baud.
3. Confirm a `DriveAlert-XXXX` advertisement and note the pairing PIN.
4. In Android, open Device Connection and scan.
5. Select the ESP32 and enter the PIN in Android’s system pairing dialog.
6. Enable the target hotspot/network and enter its SSID/password in DriveAlert.
7. Submit and confirm Android reports success and the assigned IP.
8. Confirm BLE provisioning stops and Serial never prints the Wi-Fi password.
9. Open `http://<IP>:81/stream` and confirm the OV3660 MJPEG feed.

### B — Manual connection with persisted credentials

1. Power-cycle the ESP32 without clearing NVS.
2. Confirm BLE advertises, Wi-Fi remains disconnected, and ports 80/81 are not started.
3. Connect over BLE and confirm Android offers the saved SSID without exposing its password.
4. Leave the screen without pressing Connect and confirm Wi-Fi remains disconnected.
5. Reconnect, press Connect, and confirm the saved network joins and ports 80/81 start.

### C — Wrong password

1. Clear credentials, reboot, and provision an intentionally incorrect password.
2. Confirm failure occurs after approximately 20 seconds and BLE remains available.

### D — Correct after failure

1. Retry from Android without reflashing and submit the correct values.
2. Confirm connection, persistence, BLE shutdown, and stream startup.

### E — Temporary Wi-Fi loss

1. Start normal streaming and disable the hotspot temporarily.
2. Restore it within 90 seconds and confirm reconnection without credential deletion.
3. Separately test an outage beyond 90 seconds and confirm reboot into BLE-ready mode without an automatic saved-network attempt.

## Android integration

Android stores the last successful IP for fast startup and falls back to `_drivealert._tcp` mDNS discovery when a DHCP lease changes. The app decodes the existing MJPEG stream locally and feeds the same decoded frames to its live preview and MediaPipe pipeline.

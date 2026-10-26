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
- `DriveAlert_Camera_Test/app_httpd.cpp`: camera control/MJPEG servers and DriveAlert command endpoints
- `DriveAlert_Camera_Test/warning_output.*`: DFPlayer UART setup, mappings, and queued playback
- `DriveAlert_Camera_Test/reset_button.*`: external GPIO15 reset input and polling task
- `DriveAlert_Camera_Test/reset_button_debouncer.h`: testable, one-shot debounce state
- `PROVISIONING_PROTOCOL.md`: shared Android/firmware GATT contract
- `WARNING_OUTPUT_PROTOCOL.md`: shared Android/firmware local HTTP warning contract

## Camera and HTTP behavior

The established stream implementation is preserved:

- Control/UI server: port 80
- JPEG still: `/capture`
- BMP still: `/bmp`
- MJPEG stream: `http://<ESP32-DHCP-IP>:81/stream`
- Warning output: `POST http://<ESP32-DHCP-IP>:80/drivealert/warning`
- Visibility guidance: `POST http://<ESP32-DHCP-IP>:80/drivealert/visibility`
- MIME: `multipart/x-mixed-replace;boundary=123456789000000000000987654321`

Frames come from `esp_camera_fb_get()`. Native JPEG buffers are transmitted directly and returned with `esp_camera_fb_return()`. The stream loop and fallback JPEG conversion remain unchanged.

External IR illumination is no longer controlled by DriveAlert. The illumination endpoint, lease, and GPIO2/12/15 output module have been removed. Android samples image brightness only to explain camera visibility; it does not send lighting commands. GPIO4 camera flash, GPIO13 DFPlayer TX, and GPIO14 heartbeat retain their existing roles.

Camera configuration remains JPEG at 20 MHz XCLK, initially UXGA, then QVGA after initialization. With PSRAM, the firmware uses JPEG quality 10, two framebuffers, and `CAMERA_GRAB_LATEST`. The AI-Thinker pin mapping is PWDN 32, XCLK 0, SDA 26, SCL 27, Y9..Y2 35/34/39/36/21/19/18/5, VSYNC 25, HREF 23, PCLK 22, and LED 4.

## Wi-Fi provisioning

There are no hardcoded SSID/password values in the consolidated firmware.

Preferences namespace and keys:

- Namespace: `drivealert_wifi`
- Active SSID: `ssid`
- Active password: `password`

Every boot enters BLE provisioning mode without initializing the camera or HTTP servers. Stored credentials are loaded only so Android can query whether a network is available for an explicit `CONNECT_SAVED` command. The firmware never calls `WiFi.begin()` at boot merely because NVS credentials exist.

Candidate credentials received over BLE are tested first. They replace stored credentials only after the ESP32 joins the network successfully. Failure leaves any previous known-good Preferences values unchanged and keeps BLE provisioning available. Android reuses the still-active BLE connection for an immediate retry. If that failed session remains idle for one minute, firmware disconnects it and resumes advertising as a recovery fallback; any new provisioning command cancels the fallback timer.

After the user explicitly chooses Connect and Wi-Fi succeeds, Android receives `CONNECTED|<local-ip>|<hostname>`. Firmware allows 1.5 seconds for delivery, then performs one app-authorized software restart with an RTC-memory marker. The marker is accepted only after that software restart and is immediately consumed; this second boot connects the already-validated credentials without initializing BLE, leaving enough internal RAM and socket resources for the OV3660 and HTTP/MJPEG servers. Normal power-ons and runtime-recovery reboots have no marker and always return to BLE-ready mode. Older Android builds remain compatible with the original IP-only response.

mDNS startup is attempted once per Wi-Fi connection. If the retained BLE stack leaves insufficient internal RAM for the mDNS task, firmware keeps the numeric DHCP IP and does not repeatedly retry or flood the serial console. Android already receives that current IP in the BLE success response.

### BLE security

The ESP32 uses the bundled Bluedroid implementation with:

- LE Secure Connections
- Just Works pairing without a typed PIN (no MITM protection)
- Bonding
- 16-byte encryption key
- `ESP_GATT_PERM_WRITE_ENCRYPTED` on credential/command characteristics
- `ESP_GATT_PERM_READ_ENCRYPTED` on status

No pairing PIN is generated or required. Android uses its system pairing flow and may ask for confirmation. Setup credentials remain encrypted, but Just Works does not provide PIN-based MITM protection. The Wi-Fi password is never printed, echoed in status, or included in the advertised name. After updating from PIN-based firmware, an existing phone may need to forget its old DriveAlert Bluetooth pairing once. Stored Wi-Fi credentials are retained; any legacy `ble_pin` value is ignored.

The advertised name is `DriveAlert-XXXX`, where `XXXX` is a non-secret suffix derived from the device eFuse MAC.

## Runtime recovery

- Automatic reconnect is disabled at boot and enabled only after an explicit `APPLY` or `CONNECT_SAVED` command for the current power cycle.
- During normal mode, explicit reconnect is requested every 5 seconds while disconnected.
- Credentials are not erased during transient loss.
- After 90 seconds without Wi-Fi, the ESP32 reboots.
- The next boot returns to BLE-ready mode and waits for another explicit Android command, even though credentials remain stored.

Rebooting avoids keeping BLE, camera, Wi-Fi, and the HTTP stream allocated together. These timings are engineering constants, not detection rules.

`WifiProvisioningManager::clearWifiCredentials()` safely removes only `ssid` and `password`. No physical trigger is assigned to credential erasure; the GPIO15 button only restarts the device.

## External physical reset button

- Wiring: one electrical side of a normally-open momentary switch to GPIO15; the other to GND. On a four-leg tactile switch, the two legs belonging to one electrical side are already joined. Use opposite electrical sides, not two legs on the same side.
- GPIO15 is configured as `INPUT_PULLUP`: released is HIGH and pressed is LOW. Do not connect the switch to 5 V.
- The active AI-Thinker camera map does not use GPIO15, DFPlayer TX remains GPIO13, and the heartbeat remains GPIO14. The ESP32-CAM microSD/SDIO interface is not initialized. GPIO15 is its CMD pin and cannot be shared with that interface if it is enabled later. The DFPlayer's own microSD card is independent.
- GPIO15 is also MTDO, a boot-strapping pin. Holding it LOW at reset can suppress ROM boot messages and changes SDIO slave timing; these functions are not used by this firmware. GPIO15 does not select the normal flash/download boot mode. Board-level held-button boot behavior still needs physical verification. See [Espressif boot-mode guidance](https://docs.espressif.com/projects/esptool/en/latest/esp32/advanced-topics/boot-mode-selection.html) and [SDMMC pin assignments](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/peripherals/sdmmc_host.html).
- A small FreeRTOS task samples the input every 10 ms and yields between samples. This keeps the button responsive during the existing blocking Wi-Fi scan/connect operations without changing those operations or running a restart inside an interrupt/timer callback.
- After every boot, a stable HIGH release for 50 ms arms the button. A subsequent LOW held stable for 50 ms invokes `ESP.restart()` once. Bounce restarts the interval. The request is latched before restarting, and a button held through reboot cannot cause a restart loop: release it, then press again.
- The restart clears only the temporary camera-handoff authorization. Saved Wi-Fi credentials are retained, and the existing boot behavior returns to BLE-ready mode until Android explicitly connects again. It does not automatically resume streaming or an Android monitoring session.
- This is a software restart request. If FreeRTOS cannot schedule the polling task or the firmware is completely frozen, restart cannot be guaranteed; EN/RST hardware reset or a power cycle remains necessary.

### Reset-button verification

`tests/reset_button_debouncer_test.cpp` contains seven compile-time behavior tests for released state, sustained LOW, bounce/noise, one attempt per boot, held-button reboot protection, release bounce, and `millis()` rollover. Compile it with the installed ESP32 C++ compiler using `-std=c++17 -Wall -Wextra -Werror -fsyntax-only`.

Hardware checks after flashing: verify no resets when released; verify a press resets once during BLE setup, a Wi-Fi connection attempt, and active streaming; hold the switch through reboot and confirm it waits for release; reconnect through Android and verify streaming, GPIO13 audio, and GPIO14 heartbeat. These checks require the actual wired board and have not been performed by the software tests.

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
2. Reboot; serial access is optional for diagnostics.
3. Confirm a `DriveAlert-XXXX` advertisement.
4. In Android, open Device Connection and scan.
5. Select the ESP32 and confirm Android's pairing dialog if shown; no PIN entry is required.
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

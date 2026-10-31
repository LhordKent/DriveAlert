# Camera connection recovery

## Confirmed cause and Android correction

Setup & Device used live stream state, while Device Connection selected its entire UI from the temporary BLE provisioning state. On app restart, saved device details survived but BLE state reset. The detail screen could hide Disconnect while a live Wi-Fi stream was connected. Opening Camera Alignment restored video without resetting the ESP32.

The connection route now starts the saved stream. The saved camera panel prioritizes live stream state over BLE state and shows the current device name/address. Failed video still offers Wi-Fi retry and an explicit return-to-BLE action. Control commands no longer require live video. Returning to BLE sends the existing `POST /drivealert/disconnect`, waits for the old control server to close, then exposes BLE setup. An error leaves recovery controls visible. BLE availability is verified by the subsequent scan/connection, not inferred from the HTTP acknowledgement alone.

This is an extension of the existing Android dark settings UI (Operate mode). It preserves native components, typography and status colors. A connected state must never show the remembered-device reconnect prompt. Monitoring-stopping recovery requires confirmation; failures remain actionable. Screens must remain scrollable at large font sizes.

## Optional ESP32 safeguard

The camera firmware arms a three-minute app-session grace period when normal mode starts. Only explicit heartbeats or successfully sent MJPEG frame data renew it. Wi-Fi association and generic status checks do not renew it. Expiry requests the same controlled restart into BLE-ready mode as explicit disconnect; saved Wi-Fi credentials are retained. The existing 90-second Wi-Fi-loss recovery remains in place.

Android sends `POST /drivealert/heartbeat` every ten seconds while its stream runtime is wanted, including foreground monitoring. Updated firmware responds `200 {"status":"active"}`. An unflashed device may return 404; Android accepts that as unsupported without breaking streaming. A live stream also renews the firmware watchdog, allowing older apps/stream clients to remain connected. No monitoring thresholds or alarm commands change.

The watchdog is synchronized across the Arduino and HTTP tasks, uses wrap-safe unsigned monotonic time, and does not persist session liveness across reboot. The timer is not an authentication mechanism. A stalled stream's writes are bounded by the HTTP server send timeout. Successful network writes indicate transport activity, not that a driver is being assessed.

## Completed validation

- Android: 238 unit tests passed. Three `CameraConnectionRecoveryScreenTest` UI tests passed through direct emulator instrumentation; these are the recovery checks, not the total instrumentation suite. Regression coverage includes connected UI overriding fresh BLE state, recovery without video, explicit BLE mode, confirmation and large text. HTTP tests cover heartbeat 200/404/failure and acknowledged disconnect shutdown.
- Current ESP32 hardware: after the Android update, opening Device Connection restored video automatically. Disconnect sent the command and waited for the old control server to close; a fresh BLE scan and connection then succeeded. Reconnecting with saved Wi-Fi credentials restored video without a power cycle. Phone and emulator captures are in `.impeccable/review/connection-recovery/`.
- Optional firmware: the complete `DriveAlert_Camera_Test` sketch compiled for the classic AI Thinker `esp32:esp32:esp32cam` target with installed Arduino ESP32 core 3.3.12 (sketch 1,870,160 bytes, 59%; globals 87,696 bytes, 26%). Compile-time watchdog checks passed for grace boundary, renewal, disabled state and `millis()` wraparound. This firmware was not flashed; watchdog behavior has not been tested on hardware.

The Android fix is verified with the existing firmware. Keep the current firmware for this issue, following the user's preference to avoid flashing. An additional Open Bluetooth setup action allows discovery when the camera already entered BLE mode and its Wi-Fi control server is unavailable. It does not claim to restart the camera. Remembered devices are labeled Saved device, with availability explicitly unverified.

## If the optional watchdog is flashed later

Hardware checks remain pending: maintain live video for longer than three minutes and verify no reset. Close/kill the app while Wi-Fi stays up; after three minutes without delivered frames/heartbeats, verify BLE returns and credentials remain saved. Verify app restart within the grace period restores Wi-Fi video. Verify explicit disconnect returns to BLE and reconnect with saved credentials works.

If adding the optional watchdog later, flash the complete sketch folder, including `session_watchdog.h`; updating only the `.ino` is insufficient. The three-minute safeguard becomes active only after the ESP32 firmware is flashed.

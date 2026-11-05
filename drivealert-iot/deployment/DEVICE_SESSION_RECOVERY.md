# Camera session handoff correction — 2026-11-05

The phone reached the board at its local IP and the board's authenticated HTTP server answered. A firmware defect was found in the BLE-to-camera software-restart handoff: `Handoff` used initialized `RTC_DATA_ATTR` storage while the outer authorization marker correctly used `RTC_NOINIT_ATTR`. The previous firmware map placed the session record in `.rtc.data`. This can clear the token when camera startup follows `ESP.restart()`, leaving stream, heartbeat, and return-to-BLE requests unauthorized.

The correction uses uninitialized `RTC_NOINIT_ATTR` storage with no initializer. Restore still requires the software-reset marker, session magic, token terminator and valid token format, and consumes the handoff immediately. Cold/manual restart does not restore a session. Owner keys, PIN and revocations remain in NVS. The rebuilt ELF places both the session record and outer marker in `.rtc_noinit`.

Espressif documents no-init storage as preserving values over software restart: [general memory notes](https://docs.espressif.com/projects/esp-idf/en/v3.3/api-guides/general-notes.html), [attribute definitions](https://github.com/espressif/esp-idf/blob/master/components/esp_common/include/esp_attr.h).

Android now exposes the camera error after provisioning, provides a specific unauthorized-session message, makes retry cancel the old connection attempt/backoff, and offers confirmed manual-restart recovery in the post-provisioning failure state. At the owner's request, the extra saved-camera recovery block beneath the initial Scan button was removed; it no longer appears during background retries.

## Required hardware action

Reflash the corrected firmware without erasing all flash/NVS. The PCB-enclosed board was not connected for programming and has not been flashed by this fix. An app update alone cannot repair an already-lost firmware session. After flashing, power-cycle and reconnect as the registered account through BLE; do not create a new account or erase registration. Verify the stream and normal return-to-BLE request on the actual board.

For the currently stuck board, power off/on to return it to BLE, then use “Open Bluetooth setup after restart.” This does not resolve the old firmware's next handoff; reflash is still required.

## Verification

Actual authorization host checks pass, including one-time handoff consumption and rejection on cold/manual restart. Firmware compiles for ESP32-CAM. The final ELF symbol table verifies the session object in `.rtc_noinit`; this adds evidence the earlier host-only reset simulation could not provide. Android unit tests and focused camera-recovery UI checks cover software behavior. Physical camera/session recovery remains unverified until the board is reflashed.

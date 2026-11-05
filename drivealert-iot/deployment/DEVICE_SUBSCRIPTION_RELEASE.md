# Device subscriptions: release and installation

Do not distribute the new APK with old firmware. The pairing/authentication protocol deliberately has no insecure fallback. Real payments are not configured.

The 2026-11-05 session-handoff correction requires reflashing the ESP32; updating only the APK cannot fix lost camera authorization. See `DEVICE_SESSION_RECOVERY.md`. Preserve NVS and existing ownership during flashing.

## Before installing

1. Run Android build/unit tests, the Firebase emulator suite, firmware host authorization tests, and Android migration/UI tests. Compile the classic AI-Thinker `esp32:esp32:esp32cam` firmware. The verified local compiler is Arduino-ESP32 3.3.12; Android remains on the project's pinned Iguana/JBR 17 toolchain.
2. Review the new `devices`, `deviceOwnership`, `deviceDriverAccess`, `deviceCredentials`, and owner-scoped demo event rules. Existing Trusted Contact rules remain in effect. Live deployment is a separate, explicitly authorized step; no deployment occurs from build/test commands.
3. After authorized rules deployment, verify a controlled owner can claim/invite and a second account can accept. Only then distribute the APK. Existing installations need one online registration/access check after upgrading.

## Firmware installation

1. Power off the PCB before exposing programming connections. Use the existing ESP32-CAM-MB programmer, or accessible programming pins if the PCB permits it. Removal depends on the physical wiring; no software process can determine that here.
2. Flash the updated sketch using AI Thinker ESP32-CAM, PSRAM enabled, and the existing partition/camera settings. A normal app flash preserves NVS Wi-Fi credentials. **Do not erase all flash/NVS** as a routine update; that would erase ownership, PIN, and revocation records.
3. Open the serial monitor at 115200 baud for the first boot. Record the displayed six-digit Bluetooth pairing PIN on the device label and keep a private copy. No owner/driver key or HTTP session token should appear in serial output.
4. Install the updated APK. Forget the old Bluetooth bond once if Android cannot negotiate PIN-protected pairing.
   Supply `/mp3/0016.mp3` for calibration completion and the 2.5-second `/mp3/0017.mp3` spoken countdown on the DFPlayer microSD card, preserving tracks 1–15. See `CALIBRATION_PING.md`. The audio is supplied by the owner, not bundled with this release.
5. Sign in to the primary Driver account, select the nearby board, and explicitly register it online. Enter the label PIN when Android asks. Keep initial registration under the owner's control.
6. Configure Wi-Fi/hotspot, confirm live video and physical warning playback, then turn mobile data off and repeat a power-cycle/reconnect/monitoring test.

## Additional-driver demonstration

1. Owner: Settings → Subscriptions & Drivers. Content is shown immediately. Your owned device is labeled “You are the primary driver.” A device chooser appears only if you own more than one device. Refresh online if the newly claimed device is not listed yet.
   Shortcut: Device Connection → Manage device access opens the same page directly. The screen arrow and Android Back return to Device Connection when opened this way.
2. Select the owned device, enter the other driver's connection code, verify their name, and confirm the clearly labeled demo subscription. No money is collected.
3. Recipient: use Driver mode, open Settings → Subscriptions & Drivers, refresh online, and accept the Device Invitation. The app downloads and securely saves their own offline credential.
4. Return the camera to Bluetooth setup before another phone/account takes over. The recipient selects the device and enters the pairing PIN if it is a new phone. Their credential is verified locally and their own calibration/history is used.
5. Repeat with internet disabled after acceptance. An unrelated account must fail before setup; raw BLE commands without authentication and direct unauthenticated camera HTTP requests must also fail.

## Removal

Owner removes/cancels access online. Reconnect as the owner over Bluetooth to synchronize revocation floors to the ESP32. The next old-certificate attempt must fail even when the removed phone is offline. Other active driver certificates remain valid. Reinviting issues a higher serial and a different key. The firmware stores up to 32 distinct revocation identities and reports capacity/storage errors instead of silently discarding a revocation.

## Manual gates still required

- Verify the board reports the same full BLE identity over ten power cycles and on two phones.
- Verify PIN entry/bond upgrade, auth notification timing and MTU, Wi-Fi camera handoff, and saved-session reconnect after Android process death.
- Verify owner and extra-driver use with mobile data off, same-phone account switching, wrong-account rejection, physical speaker warnings, and offline revoked-credential rejection after owner sync.
- No PCB was available during development; software/emulator success is not a claim that these physical checks have passed.

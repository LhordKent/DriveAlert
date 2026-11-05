# Device subscription verification

Verified on 2026-11-04, with follow-up changes on 2026-11-05, on `codex/device-subscriptions`. Main remains at `83c9c85c2c84952e1b90b45f8adb77d2cbe974e0`. No physical flash, commit, or push was performed. With explicit owner approval on 2026-11-05, Firestore rules were deployed to `drivealert-lhordkent`; refreshing on the connected phone then showed “Device access refreshed.” No data was cleared.

Subscription and driver-access management is now under Settings → Subscriptions & Drivers. Device Connection retains registration and pairing and provides a “Manage device access” shortcut. The updated APK was installed on the TECNO phone with `adb install -r`, without uninstalling or clearing data. Its shortcut destination and Android Back return to Device Connection were verified on the phone. The existing root-navigation instrumentation check also passed with both app Back and Android Back assertions.

The subscription page now shows all content directly, without an expand button. Primary ownership and included access are explicit; a single owned device is plain text. A chooser appears only for more than one primary-owned device and has 48dp minimum touch targets. Three focused UI tests pass, covering confirmation/cancellation, recipient actions, primary-owner/offline presentation and switching between multiple owned devices. The primary-owner capture passed the scoped visual review; multi-device visuals and large font settings remain unverified visually.

## Software evidence

- Android debug APK builds with the existing pinned toolchain. All 275 JVM checks pass, covering credential HMAC vectors, canonical identity, HTTP response identity, all four measured calibration completion edges, ping/countdown HTTP responses, the 2,500 ms preparation schedule, and the existing application behavior.
- Fourteen focused Android instrumentation checks cover Room 6→7 migration, preserved legacy connections without automatic grants, UID isolation, revision tombstones, encrypted credential/session persistence, explicit simulated activation/cancellation, invitation acceptance/decline, camera recovery, and existing shared-record error handling. Device: Pixel 7 API 35 emulator.
- All 31 Firebase emulator checks pass: 19 existing Trusted Contact regressions and 12 device access cases, including claim races, atomic credentials/events, owner-only invitation, recipient acceptance, secret privacy, immutable owner keys, and reinvitation revisions. Expected permission-denied messages are negative-test evidence.
- The actual firmware authorization implementation passes host tests with real Windows CNG HMAC-SHA256: ownership persistence/replacement rejection, storage failure, certificate verification, nonce expiry/replay, HTTP sessions, reboot/handoff, and revocation persistence.
- Arduino-ESP32 3.3.12 compiles the AI Thinker ESP32-CAM sketch including calibration ping/countdown and corrected session handoff: 1,881,052 bytes program storage (59%); 89,120 bytes globals (27%).
- Existing firmware warning-output and session-watchdog host checks also pass.
- After adding calibration ping, the two calibration UI/recovery tests pass on both Pixel 7 API 35 emulator and the connected TECNO LE6h Android 11 phone. The hardware speaker and supplied track 16 were not tested. The warning-output host test additionally verifies track 16, medium volume, priority, duplicate queue rejection and stale-cue expiry. The firmware authorization host test still passes. See `CALIBRATION_PING.md` for the SD-card installation and manual checks.
- After adding the 2.5-second track-17 countdown, all six calibration instruction/recovery tests pass on the Pixel 7 API 35 emulator, including no early collection, one preparation request, failed-start rejection and redo acknowledgement. Firmware host checks verify track 17 dispatch signalling, 2,500 ms reservation and warning precedence. Physical playback/timing of the owner-supplied tracks 16/17 remains unverified.

## Existing test failures

The broader `PostAuthFlowTest` suite has three failures. All three were reproduced from a clean source archive of unchanged `main` on the same emulator:

- `freshAuthenticationRequiresLiveCalibrationBeforeMonitoring`: expected component outside the displayed viewport.
- `everyMonitoringAvailabilityScenarioHasPlainLanguageStatus`: expected component outside the displayed viewport.
- `driverHomeSeparatesMonitoringToggleFromCameraPreview`: fixture does not reach the expected Face mask prompt.

They were left outside this feature's scope. Eight of that suite's eleven tests pass on main. Do not report the whole instrumentation suite as green.

## Native UI review

The simulated-activation dialog and recipient invitation were captured on Pixel 7 API 35. Review found no material clipping or system-bar overlap and confirmed explicit no-payment copy and separate-history wording. Action contrast was corrected within the new flow: light text on the established darker red button token, and light dialog action labels. The final scoped review found no remaining material findings: acceptance label 4.97:1, dialog confirmation 12.94:1, cancellation 8.20:1. Shared button defaults and existing flows retain their existing styling.

Font scaling, long-name captures, TalkBack, tablets, and physical device interaction remain unverified. The screenshot's fallback inviter name does not independently verify production profile mapping.

## Release limits

A real phone/board connection exposed a session-handoff defect missed by host reset simulation. It is corrected in source and the compiled ELF now verifies `.rtc_noinit` placement. The PCB-enclosed board has not been reflashed, so physical stream/recovery success is still pending. See `DEVICE_SESSION_RECOVERY.md`; do not treat previous software-test success as proof of a physical handoff.

Software checks do not validate physical BLE PIN entry, MTU/notification timing, stable board identity, Wi-Fi/camera handoff, physical warning output, or a complete two-account/same-phone hardware demonstration. Follow `DEVICE_SUBSCRIPTION_RELEASE.md` before use. Deploy the reviewed Firestore rules only with release authorization, and update app and firmware together.

Setup, invitation and acceptance require internet. Subsequent daily use verifies cached account-specific credentials offline. Owner removal requires an owner BLE synchronization before the board can reject an offline removed driver. Initial ownership assumes trusted physical installation. This prototype does not protect against physical reflashing or a compromised phone/network. Simulated subscriptions have no real payments or recurring billing engine.

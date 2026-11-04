# DriveAlert-MVP

A self-contained classroom Android project using strict Model-View-Presenter (MVP). It is independent from the main DriveAlert Android application even though it lives in the same repository.

## Open and run

1. Open `C:\DriveAlert\drivealert-mvp` as the project root in Android Studio.
2. In the existing `drivealert-lhordkent` Firebase project, register the separate Android app package `com.drivealert.mvp`.
3. Use only the project's Realtime Database path `/devices/esp32_01`; the capstone's Cloud Firestore data is unrelated to this MVP.
4. Keep Email/Password Authentication enabled and sign in with an existing DriveAlert account from this Firebase project.
5. Download the configuration for the `com.drivealert.mvp` Android app into `app/google-services.json`.
6. Publish `database.rules.json` for the project's Realtime Database only. Do not change Cloud Firestore rules or data.
7. Build and run the Android app.
8. Configure and upload `esp32/DriveAlertMvpLed/DriveAlertMvpLed.ino` to the ESP32. It uses the ESP32 core `WiFiClientSecure` and `HTTPClient` libraries, so no Firebase Arduino library or ESP32 Firebase account is required.

The Android repository explicitly connects to `https://drivealert-lhordkent-default-rtdb.asia-southeast1.firebasedatabase.app/`. The build skips the Google Services plugin when `google-services.json` is absent, so source and unit tests remain buildable before Firebase registration.

Firebase Authentication is project-wide. The MVP reuses existing DriveAlert email/password users and does not require an MVP-only Android or ESP32 account. The ESP32 uses unauthenticated access limited by the Realtime Database rules to reading `command` and reporting the three device-state fields.

## Realtime Database data

```text
devices/
  esp32_01/
    command: "ON"
    actualLedState: true
    status: "online"
    lastUpdate: 1710000000000
```

- Android writes only `command`.
- ESP32 polls `command` once per second, changes the physical LED, and then writes `actualLedState`.
- ESP32 refreshes `status` and `lastUpdate` every 10 seconds.
- Android marks the ESP32 offline when the heartbeat is older than 25 seconds.
- Android treats an online heartbeat older than 60 seconds as offline.
- Missing or invalid device data is shown as unavailable.

The requested command and actual LED state are deliberately independent. A successful Android write means the command reached Firebase, not that the physical LED has changed.

## Architecture

- View: `LoginActivity`, `LedActivity`, and their Compose UI.
- Presenter: handles validation, commands, state mapping, navigation decisions, and feedback.
- Model/Repository: Firebase Authentication and Realtime Database operations/listeners.

There are no ViewModels, StateFlows, MVVM/MVI layers, use cases, or dependencies on the root `app` module.

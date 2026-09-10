# DriveAlert

DriveAlert is a native Android application built with Kotlin and Jetpack Compose. Firebase Authentication provides identity, Room/SQLite is the Driver's authoritative offline database, and Cloud Firestore supports profiles, connections, and eligible Stage 3 sharing.

The free Firestore connection-code workflow is implemented and documented in [CONNECTION_CODE_PLAN.md](CONNECTION_CODE_PLAN.md). It works without Cloud Functions or the Blaze plan.

## Transfer this project to another computer

On the laptop:

```powershell
git clone https://github.com/LhordKent/DriveAlert.git
cd DriveAlert
```

If the repository is already cloned:

```powershell
git status
git pull --ff-only origin main
```

Do not pull over uncommitted laptop changes. Commit, stash, or copy them somewhere safe first.

## Required development environment

- Android Studio Iguana 2023.2.1
- JDK/JBR 17
- Android SDK Platform 34
- Android SDK Build Tools 34.0.0
- Git
- Node.js 22 for the Firebase rules test workspace
- JDK 21 or newer for the Firebase Emulator Suite only

The repository pins the compatible Gradle, Android Gradle Plugin, Kotlin, Compose, Navigation, and Firebase versions. Do not accept automatic upgrades that would make the project incompatible with Android Studio Iguana. Android builds must continue using JBR 17 even when a newer JDK is installed for Firebase emulator tests.

## First setup on the laptop

1. Install Android Studio Iguana 2023.2.1.
2. In SDK Manager, install Android SDK Platform 34, Build Tools 34.0.0, Platform Tools, and Android Emulator.
3. Open the cloned repository root, not the `app` folder.
4. Set **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK** to Android Studio JBR 17.
5. Allow Gradle sync to complete. The Gradle wrapper downloads Gradle 8.4 automatically.
6. Create or select an Android emulator and run the `app` configuration.

Android Studio generates the ignored, machine-specific `local.properties`. The versioned `app/google-services.json` already selects Firebase project `drivealert-lhordkent`; no file copying from the old computer is required. A collaborator needs Firebase Console permission only for managing the cloud project, not for compiling the Android app.

## Open and run

1. Clone the repository.
2. Open the repository root in Android Studio Iguana.
3. Confirm Gradle uses JDK 17.
4. Allow Gradle sync to finish.
5. Run the `app` configuration on an Android emulator or device.

Firebase Authentication is preconfigured for package `com.lhordkent.drivealert` and project `drivealert-lhordkent` through the versioned `app/google-services.json` file.

The app starts with Firestore's disk persistence disabled. Trusted Contact cloud screens therefore require internet after an app-process restart. Android automatic backup is also disabled so local monitoring records are not copied through Android backup services.

## Data architecture

- Room/SQLite stores UID-scoped monitoring sessions, confirmed Alerts and signs, calibration history, Driver preferences, Stage 3 synchronization records, and the minimal offline relationship projection.
- Firestore stores user profiles, Driver–Trusted Contact relationships, and synchronized Stage 3 records.
- Room is the local source of truth, not a cache for Firestore. Monitoring and confirmed Alert persistence do not depend on internet access.
- An ordinary Alert never creates cloud work automatically. The Android Warning Stage state machine explicitly creates separate `STAGE3_TRANSITION` and `STAGE3_PERSISTENCE` synchronization records at eligible boundaries.
- Device provisioning, ESP32 camera streaming, MediaPipe face-landmark processing, driver calibration, temporal sign confirmation, active monitoring, warning stages, notifications, and Room Alert History are implemented. Physical ESP32/DFPlayer warning delivery remains a pending hardware boundary.

The configured Firestore database is `(default)`, Standard edition, in `asia-southeast1` (Singapore). The checked-in Firestore rules and indexes are local source files only; they are not deployed automatically.

## Command-line verification

Android Studio creates the machine-specific `local.properties` file automatically. When running Gradle from a terminal before opening the project, set `ANDROID_HOME` to the Android SDK installed on that computer. Do not commit `local.properties` because its path is different for every developer.

On Windows with JDK 17 and the Android SDK selected:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestSources
```

With an emulator or device running:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. A ready-to-install copy of the latest verified debug build is also versioned as `DriveAlert-debug.apk` at the repository root.

## Firebase backend development

Connection requests use authenticated Firestore reads and transactions. Each account receives a random 128-bit connection code. No Cloud Function, external email, or paid Firebase plan is required.

Install the current Firebase test dependencies from the lockfile:

```powershell
cd firebase-tests
npm ci
```

To run the Auth and Firestore rules integration tests locally, use a separate JDK 21 or newer for the Firebase Emulator Suite. Keep Android Studio/Gradle on JBR 17 for Iguana compatibility.

```powershell
$env:JAVA_HOME = "C:\Path\To\JDK-21-Or-Newer"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd firebase-tests
npm run test:emulator
```

Both a Driver and Trusted Contact can initiate a request using the other person's exact code. The target account is shown before sending; the recipient approves or declines, and the sender may cancel.

Do not deploy from normal build or test workflows. Rules or indexes deployment requires an explicit project-owner decision and `firebase deploy --only firestore` must be run manually. The currently deployed test-mode Firestore rules are not suitable for real user data and must be replaced with the checked-in reviewed rules before cloud data is used outside controlled development.

## Inspect Room/SQLite

Room creates `drivealert.db` in the installed app's private storage:

```text
/data/data/com.lhordkent.drivealert/databases/drivealert.db
```

Run the debug app and open **View > Tool Windows > App Inspection > Database Inspector** in Android Studio. Production Room starts empty. Room-backed preference changes create rows; Alerts and monitoring sessions appear only when their eventual production pipelines write them. The version-controlled schema is in `app/schemas/`.

## Codex continuation checklist

Before changing this repository, Codex or another coding agent must:

1. Read `PRODUCT.md`, `DESIGN.md`, this README, and `CONNECTION_CODE_PLAN.md`.
2. Inspect `git status --short` and preserve unrelated dirty-tree changes.
3. Keep AGP 8.3.2, Gradle 8.4, Kotlin 1.9.22, Compose Compiler 1.5.10, SDK 34, and JBR 17 for Android.
4. Keep package/application ID `com.lhordkent.drivealert` and the authoritative logo unchanged.
5. Do not restore deleted prototype screens or edit `drivealert-ai-prototype/` unless explicitly requested.
6. Use `apply_patch` for source edits.
7. Never seed production Room or Firestore from previews or tests.
8. Never deploy Firebase resources, commit, or push without explicit authorization.
9. Run the Android and Firebase emulator tests appropriate to the changed slice.

For connection-code work, preserve the implemented architecture in `CONNECTION_CODE_PLAN.md`. Do not add a paid service or revive email lookup.

## Current scope

- Real Firebase email/password authentication, session restoration, account roles, and sign-out
- Driver and Trusted Contact navigation
- BLE Wi-Fi provisioning and ESP32 MJPEG camera connection/reconnection
- MediaPipe landmark processing with calibrated EAR, MAR, and head-pitch temporal detection
- Real monitoring sessions, live duration/event state, and the three-stage 60-second warning controller
- Android warning notifications and a pending/no-op physical speaker gateway
- Room-backed production Alert History, Alert Detail, monitoring-session summaries, preferences, and Stage 3 synchronization queue
- Firestore-backed profiles, bidirectional connection requests, shared Stage 3 timelines, and background WorkManager synchronization

Production Room starts empty; preview fixtures and UI-test fakes do not seed production data. Physical ESP32/DFPlayer speaker delivery and external email delivery are not implemented.

# DriveAlert

DriveAlert is a native Android application built with Kotlin and Jetpack Compose. The current post-authenticated experience uses in-memory mock device, calibration, monitoring, alert, and Trusted Contact state while Firebase Authentication is real.

## Required development environment

- Android Studio Iguana 2023.2.1
- JDK/JBR 17
- Android SDK Platform 34
- Android SDK Build Tools 34.0.0

The repository pins the compatible Gradle, Android Gradle Plugin, Kotlin, Compose, Navigation, and Firebase versions. Do not accept automatic upgrades that would make the project incompatible with Android Studio Iguana.

## Open and run

1. Clone the repository.
2. Open the repository root in Android Studio Iguana.
3. Confirm Gradle uses JDK 17.
4. Allow Gradle sync to finish.
5. Run the `app` configuration on an Android emulator or device.

Firebase Authentication is preconfigured for package `com.lhordkent.drivealert` and project `drivealert-lhordkent` through the versioned `app/google-services.json` file.

## Command-line verification

On Windows with JDK 17 selected:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug compileDebugAndroidTestSources
```

With an emulator or device running:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Current scope

- Real Firebase email/password authentication and sign-out
- Driver and Trusted Contact navigation
- Session-only mock setup, device connection, calibration, and monitoring states
- Mock warning-event history and Stage 3 synchronization records
- Session-only contacts, requests, profile preferences, sounds, and notification settings

No camera processing, IoT communication, hotspot control, warning timers, detection engine, persistence database, or real synchronization service is implemented yet.

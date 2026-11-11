# DriveAlert System Audit — 2026-11-11

## Current status (plain English) — last updated 2026-11-11

**In one line:** Everything we can test without the camera passes. The two most serious bugs are fixed and live. Some smaller issues are written down but not fixed yet.

### ✅ Done
- **Alerts getting stuck (F1): fixed and live.** One failed alert used to stop all of a driver's later Stage 3 alerts from reaching their contacts. Fixed in the app and in the alert server, and the server change is deployed.
- **Phone stuck "Starting monitoring" (F4): fixed.** If something changed while monitoring was starting up, the phone could keep a notification and drain the battery. It now stops cleanly and shows why.
- **Full test run: all green.** That covers the Android app, the web portal, the database security rules, the alert server and the camera firmware logic (on the PC, not the device).
- **Outdated test: fixed.** One test expected old on-screen wording; it now matches the app.
- **Everything above is saved to GitHub.**
- **New: sharing notice on connection requests (saved to GitHub).** Before sending a request, the phone app and the website now say: "Your name (…) and email (…) will be visible to the person you send this request to." Tests pass, but it hasn't been checked on screen.

### 🟡 Known but not fixed yet (in order of importance)
1. **F2 (lowered to Low–Medium):** a connection request shows the sender's name and email, but the sender's own app supplies them, so a tampered app could fake them. It only works for someone who already has your connection code. **Decision (2026-11-11):** for now we only added a notice telling senders what's shared. The behind-the-scenes block on faked emails was skipped on purpose.
2. **F3:** a stranger near the camera could register it to their account before you do.
3. **F5:** signing up on the web, then logging in from another browser, can leave the account without a profile.
4. **Smaller items (F6–F11):** alert times shown in UTC, error messages that disappear too fast, and similar.

### 👀 Keep an eye on
- **IT-07 test:** failed once, then passed twice. It might mean a connection request sometimes says "already exists" even though it worked. Not confirmed.

### ❌ Not covered
- Anything that needs the physical camera: Bluetooth pairing, the live video stream, the speaker.

---

## Technical details

Scope: Firestore rules, notification-worker (Cloudflare), web portal, Android app (auth, sync, device access, monitoring service), ESP32 firmware authorization and HTTP layer. Every finding below was traced through the code. When the audit was first written, nothing had been modified; fixes since then are listed under **Current status** above.

## Summary

| ID | Severity | Area | Title |
| --- | --- | --- | --- |
| F1 | High | Worker + Android sync | A delivery row whose installation was deleted keeps its group "pending" forever, and this blocks every later Stage 3 alert from that driver |
| F2 | Low–Medium | Firestore rules | Connection requests accept a made-up email (and name) for the requester. The attacker must already have the target's connection code. |
| F3 | Medium | Firestore rules / device claim | Anyone who knows a camera's Bluetooth MAC can claim it in Firestore first, and users have no way to recover it themselves |
| F4 | Medium | Android monitoring | Monitoring start can end without calling either callback, leaving the foreground service and wake lock stuck |
| F5 | Medium (web) / Low (Android) | Web + Android auth | Profile details are only saved on the device used to register. On the web, signing in elsewhere leaves no profile; Android creates a fallback profile but loses the structured name and phone number |
| F6 | Low | Worker | Notification text shows record times in UTC |
| F7 | Low (policy-dependent) | Firestore rules | Users can change their own `accountStatus` (and `userRole`), so a deactivation can't be enforced |
| F8 | Low | Android monitoring | A start error is posted and then removed straight away, so the user never sees it |
| F9 | Low | Android monitoring | Stopping during start can leave an orphaned ACTIVE session row |
| F10 | Low | Worker | Groups that never complete are never purged, and the purge runs on every request |
| F11 | Low | Web + Android | Connection-code display names differ by platform (middle name included or not) |

---

## F1 — High: Stage 3 alerts stop for good once one delivery is orphaned

**Location:** `notification-worker/src/index.ts:120-157`, `app/.../data/sync/StageNotificationDispatcher.kt:34-60`, `CloudflareStageNotificationRemoteDataSource.kt:34`

**What's wrong**
- `finalizeGroup` picks pending deliveries with an `INNER JOIN installations`, but it counts pending rows from `device_deliveries` alone, with no join.
- If an installation is deleted while one of its deliveries is still `PENDING`, that row is never sent and never marked terminal. The pending count stays above zero, so every retry returns `202 DELIVERY_RETRY`.
- An installation is deleted when:
  - the user signs out (`DELETE /v1/devices/:id`)
  - the FCM token is re-registered under a new installation ID (`index.ts:41`)
  - the user goes over the 5-installation limit (`index.ts:45`)
  - another group gets `INVALID` for that token (`index.ts:136`)
- On Android, 202 maps to `AwaitingMore` and `dispatchPending` returns `false` at the first such group. Groups are processed oldest first, and there's no attempt limit. So the stuck group blocks every later group for that driver, permanently.

**Failure scenario**
1. Driver D has an approved trusted contact C with installation I.
2. Group G1 finalizes. FCM returns 503, so the delivery `(G1, C, I)` stays `PENDING`.
3. C signs out of the app, which deletes installation I.
4. Every retry of G1 now returns 202, and D's later Stage 3 groups G2, G3 and so on are never dispatched. None of D's trusted contacts get another push.

**Suggested fix**
- In `finalizeGroup`, use a `LEFT JOIN` and mark rows with no installation as `TERMINAL` (`INSTALLATION_REMOVED`).
- Or delete or terminate `device_deliveries` rows in the same batch whenever an installation is deleted.
- On Android, add an attempt cap or age limit per group, and don't let one group block the groups after it.

## F2 — Medium: Requesters can make up their own name and email

**Location:** `firestore.rules:71-87` (`validConnectionCreate`), `firestore.rules:112-129` (re-request)

**What's wrong**
- The rules check the field types of `driverName`, `driverEmail`, `trustedContactName` and `trustedContactEmail`, but not their values.
- The recipient's app shows these strings in the request (`PostAuthViewModel.kt:712-779`).
- The worker puts `driverName` into the push text (`notification-worker/src/firestore.ts:55`, `fcm.ts`).
- Device invitations already check names against `connectionCodes/*.displayName` (`firestore.rules:286-288`), but connection requests don't.

**Failure scenario:** a malicious user writes the connection document directly, with `trustedContactName: "Mom"` and `trustedContactEmail` set to the real parent's address. The driver sees a request that appears to come from their parent, approves it, and the attacker can now read the driver's Stage 3 history.

**Suggested fix**
- **Email (this is what stops the spoofing):** require the requester's email field to equal `request.auth.token.email`. The rules already require `email_verified`, so that email is proven to belong to the requester. Leave the target's email empty, as the clients already do. Show this verified email prominently on incoming requests so the recipient can recognize the sender.
- **Names (consistency only):** requiring names to match the `connectionCodes` `displayName` keeps them consistent across screens, but users choose their own display names. This can't prove that someone really is "Mom", so the UI shouldn't treat the name as an identity check.

## F3 — Medium: Device IDs can be claimed by someone else first

**Location:** `firestore.rules:242-263`, `app/.../data/device/DeviceAccessRepository.kt:74-91`

**What's wrong**
- The device ID is the public Bluetooth MAC (`device_authorization.cpp: deviceId()`), which the camera broadcasts in its BLE advertising.
- The rules allow any verified user whose profile says DRIVER or BOTH (a field they control, see F7) to create `devices/{id}` and `deviceOwnership/{id}` if no one has yet.
- Clients can't update or delete those documents, and the app has no recovery or transfer flow. An administrator could repair the records through the Firebase console or Admin SDK, but there's no documented procedure.

**Failure scenario:** a nearby attacker scans the camera's BLE address and claims it in Firestore before the real owner sets it up. The owner's `claim()` then fails with "This device is registered to another account". The firmware still accepts the owner's BLE `CLAIM`, but cloud features such as inviting drivers and syncing credentials stay blocked until an administrator steps in by hand. The protocol doc covers the trust model for the device itself, but not this cloud-side squatting.

**Suggested fix**
- Require proof of possession before the cloud claim, for example a signature or HMAC from the device's pairing PIN or factory secret, checked by a backend function.
- At a minimum, document an admin repair procedure: delete `devices/{id}`, `deviceOwnership/{id}` and the related `deviceCredentials`, and check the device's NVS owner record.

## F4 — Medium: Monitoring start can leave the service stuck

**Location:** `app/.../detection/DriverVisionViewModel.kt:642-645`, `app/.../monitoring/DriveAlertMonitoringService.kt:181-212`

**What's wrong**
- The service sets `startInProgress = true` and waits for `onStarted` or `onFailure`.
- After the session row is created, the runtime checks `expectedAccount != accountGeneration || !mayUse(device)`. If that check fails, it marks the session INTERRUPTED and returns without calling either callback.
- The service keeps its "Starting camera monitoring" foreground notification and renews the partial wake lock every 9 minutes (`:278-283`).
- Later START intents are ignored, because `monitoringStartAllowed` returns false while `startInProgress` is true.

**Failure scenario:** device access is revoked by a concurrent `DeviceAccessRepository.refresh`, or the account changes, while `monitoringSessionRepository.start` is running. The phone then holds a wake lock and an ongoing notification indefinitely, and monitoring can't be restarted until the user taps Stop.

**Suggested fix:** call `onFailure(IllegalStateException("Device access changed. Verify access and retry."))` before that `return@launch`.

## F5 — Medium: Registration only finishes on the device used to sign up

**Location:** `drivealert-web/src/repositories/authRepository.ts:24-32,55-60`, `app/.../auth/PendingRegistrationStore.kt`, `app/.../auth/AuthViewModel.kt:89-110`

**What's wrong:** the profile fields from sign-up are kept only in `localStorage` (web) or `SharedPreferences` (Android) until the email is verified. The Firestore profile is created only on that same device and browser.

**Failure scenario**
- **Web (Medium):** a user registers on the web portal, opens the verification link on their phone, then signs in from a different browser or after clearing site data. They get stuck on "Profile not found". The web has no fallback.
- **Android (Low):** after a reinstall or data clear before verification, there's no pending record, but `PostAuthViewModel.kt:129` calls `ensureProfile()`, which creates a fallback profile from the Firebase Auth details (`UserProfileRepository.kt:57-88`). The account works, but the whole display name goes into `firstName`, `lastName` is set to `""`, and `middleName` and `phoneNumber` are lost.

**Suggested fix**
- Web: add the same fallback as Android. When a verified user has no profile and no pending record, create a Trusted Contact profile from `user.displayName` and `user.email`, and let them edit it afterwards.
- Both platforms: to keep the structured fields too, create the profile right after `createUserWithEmailAndPassword`. That needs a rule exception that allows creating your own profile while unverified.

## F6 — Low: Push notification times are in UTC

**Location:** `notification-worker/src/fcm.ts:13-14`

**What's wrong:** `toLocaleTimeString(..., { timeZone: 'UTC' })` formats the "recorded 1:00 AM–1:05 AM" text. Anyone outside UTC sees the wrong time, for example 8 hours off in UTC+8.

**Suggested fix:** send the epoch millis in the FCM data payload and format the time on the recipient's device.

## F7 — Low: Role and status are under the user's own control

**Location:** `firestore.rules:134-139`, `firestore.rules:235-238`

**What's wrong:** `users/{uid}` updates may change `userRole` and `accountStatus` freely. How much this matters depends on the intended policy:
- **`accountStatus`:** if DEACTIVATED is meant as an enforced suspension, this is a real gap, because the user can set it back to ACTIVE themselves.
- **`userRole`:** the app lets users choose their own role (`selectRole`), so this is probably intended. In that case `activeDeviceDriver()` only stops honest clients, not a modified one, and shouldn't be relied on as a security boundary.

**Suggested fix:** if deactivation is enforced, block client changes to `accountStatus` and set it only from an admin or backend path. Leave `userRole` user-controlled if that's the product intent, and optionally restrict it to the transitions the app actually uses.

## F8 — Low: Start errors are never shown

**Location:** `DriveAlertMonitoringService.kt:257-260`

**What's wrong:** `failStart` posts a foreground notification with the error text, then `finishService` immediately calls `stopForeground(STOP_FOREGROUND_REMOVE)`, so the message disappears.

**Suggested fix:** post the error as a separate, non-ongoing notification, or use `STOP_FOREGROUND_DETACH`.

## F9 — Low: Orphaned ACTIVE session when stopped mid-start

**Location:** `DriverVisionViewModel.kt:626-647,687-692`

**What's wrong:** `stopMonitoring` cancels `monitoringStartJob`. If the cancel lands after `monitoringSessionRepository.start` has returned but before `monitoringController.start` runs, `monitoringController.state.sessionId` is still null. The new session row is never finished, so it stays ACTIVE in Room and shows up in monitoring logs.

**Suggested fix:** in the start job's `catch (CancellationException)`, finish the created session as INTERRUPTED, using `NonCancellable`.

## F10 — Low: Worker housekeeping

**Location:** `notification-worker/src/index.ts:174-176`

**What's wrong:** only groups with `completed_at` set are purged. Groups stuck in RECEIVING (missing chunks) or in delivery retry stay in D1 forever. The purge `DELETE` also runs on every request, including device registration.

**Suggested fix:** purge by `created_at` age as well, and move the purge to a scheduled (cron) trigger.

## F11 — Low: Display names differ by platform

**Location:** `app/.../data/profile/UserProfileRepository.kt:54` (`firstName + " " + lastName`) vs `drivealert-web/src/repositories/profileRepository.ts:46` (`fullName` with middle name)

**What's wrong:** the same person's connection-code `displayName` depends on which platform created it, so a name with a middle name shows up differently in requests and invitations. The web profile edit rewrites it with the middle name.

**Suggested fix:** use one shared formatting rule.

---

## Cross-component contract check

| Contract | Result |
| --- | --- |
| Android `StageSyncRemoteDataSource.upload` vs `stageSyncRecords` rule | Matches: every key, type, `uploadedAt == request.time`, record type values |
| Stage record → worker `readStageRecord` | Matches. The worker also accepts the legacy `STAGE3_*` values. |
| Android dispatch chunks vs worker validation | Matches: 20 records per chunk, ID pattern, deterministic `batchId` |
| Worker status codes → Android result mapping | 202 is used for both `AWAITING_CHUNKS` and `DELIVERY_RETRY`, and Android can't tell them apart (this feeds into F1) |
| Web stage query vs rules and indexes | `driverUserId ==` + `periodStartedAt >= approvedAt` + `orderBy desc` is covered by the index and the `approvedConnection()` rule |
| Device invitation (Android) vs `validDeviceInvitation` | Matches: revision/serial, event document, credential `createdAt`, display names |
| Credential crypto (Android `DeviceCredentialCrypto`) vs firmware `device_authorization.cpp` | Same message formats (`AUTH|`, `DRIVER|`, `CERT|`), uppercase hex, revocation floors match the doc |
| Firmware HTTP handlers | All 17 registered handlers call `requireHttp` first. `/resolution` reuses `win_handler`, which is also checked. |
| Connection name fields | Not enforced by rules (F2) |

## Test-suite results

| Suite | Command | Result |
| --- | --- | --- |
| Android JVM unit tests | `./gradlew testDebugUnitTest` (Android Studio JBR 17) | 286 tests in 45 classes, all passed |
| Web typecheck + lint + Vitest | `npm run typecheck && npm run lint && npm test` | Typecheck and lint clean; 69 tests in 19 files passed |
| Notification worker | `npm run check` | Typecheck clean; 7 tests in 2 files passed |
| Firestore rules (emulator) | `firebase emulators:exec … "npm --prefix firebase-tests test"` | 32 passing |
| Android instrumented, Playwright e2e, firmware host tests | — | Not run (they need an emulator or device, a browser-plus-emulator stack, or a Windows CNG host build) |

Environment notes:
- `JAVA_HOME` isn't set in this shell, so Gradle needed the Android Studio JBR.
- firebase-tools needs Java 21 or newer, so I used `~/.jdks/openjdk-23.0.2`.
- Port 8080 is taken by `steamwebhelper`, so the rules suite ran with the Firestore emulator on port 8085, using a temporary config outside the repo.

All existing suites pass. None of them exercise the failure paths behind F1–F5.

## Coverage gaps worth adding

- Worker: a test where an installation is deleted while a delivery is pending and the group must still complete (F1).
- Android: `StageNotificationDispatcher` with one permanently-202 group followed by other groups (head-of-line blocking).
- Rules: tests that a connection create with a forged `trustedContactName` or `driverName` is denied, once F2 is fixed.
- Service: a test that `startMonitoring` always calls exactly one of `onStarted` or `onFailure`.

## Not covered by this audit

- On-device behavior: BLE pairing timing, MJPEG streaming under packet loss, speaker output.
- MediaPipe and FaceAttribNet model accuracy, and the detection thresholds in `TemporalDetectors`. I read them for crashes only, not for whether the thresholds are tuned well.
- Android instrumented tests (`androidTest`), which need an emulator or device.

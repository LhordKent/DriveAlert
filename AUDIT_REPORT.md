# DriveAlert System Audit — 2026-11-11

## Current status (plain English) - last updated 2026-11-27

### 2026-11-27 — Published sign-up and invitation-name updates
- The website, Firestore rules, and invitation Worker are live. Verified the served web assets against the tested build, read back the active rules, and confirmed the new Worker version serves traffic. The live form passed checks at desktop and phone widths without creating accounts. The live Firebase policy passed the password boundary and special-character checks.
- Release tests passed on the signed APK and local Firebase. All 10 Android, six browser, 37 Firestore rules, and 20 Worker checks passed. Tests covered email verification and profile edits with and without a suffix, sign-in with an older password, and replacing it through the reset email.
- The signed Android installer is `output/DriveAlert-2026-11-27-signup-suffix-and-password.apk`. Its signing certificate matches the previous installer. No physical phone was connected for installation.

### 2026-11-27 — Stronger passwords for sign-ups and resets
- Live Firebase Authentication now requires at least eight characters with an uppercase letter, a lowercase letter, and a special character. A digit is optional. Existing passwords remain valid for sign-in.
- Read back the live settings and checked valid and invalid samples through the Firebase SDK without creating or changing an account. Android and web sign-up now explain and check the same requirements before submission. Both builds passed, along with 345 Android unit tests, nine Android emulator tests, 69 web unit and component tests, and three browser tests.

### 2026-11-27 — Optional suffix on Android and web sign-up
- **Suffix (optional)** follows Last name. First, middle, and last name fields remove numbers during typing or pasting. The suffix allows numbers, trims surrounding spaces, and is saved with the profile after email verification if filled in. Full names and new invitations include it, and editing other profile details preserves it.
- Passed 345 Android unit tests, nine Android emulator sign-up and authentication checks, 68 web unit and component tests, five browser tests, 37 Firestore rules tests, and 20 Worker tests. The browser sign-up test verified the name after email verification, sign-in, and a profile edit. The Android and web builds passed.
- The Firebase rules, website, and Worker are live. The Android installer is ready and physical-device installation is pending.

### 2026-11-25 — Show additional driver names and phone numbers
- The read-only Worker route checks device ownership and current membership, then returns the saved phone number. Android omits the Active · Demo subscription row label. Passed 345 Android unit tests and 20 Worker tests, including the Firebase emulator integration. Android UI tests compiled. The Android emulator lacked disk space, so the list was verified on the updated TECNO with an existing membership. Worker version `91473325-f0ae-4c50-8540-7e51d6306046` is live. Installed in place at 11:12 PM Manila with app data preserved.

### 2026-11-25 — Fix BLE provisioning status text
- Corrected a garbled ellipsis in the saved-network check message. Android build and unit tests passed. Installed in place on the TECNO at 11:00 PM Manila and verified the account, settings, and alert database.

### 2026-11-25 — Match the Remove driver button to Search
- Subscriptions & Drivers uses the same filled red button for Remove driver and Search. Removal still requires confirmation. Built the debug APK and passed Android unit tests. Included in the 11:00 PM Manila TECNO update.

### 2026-11-25 — Remove ended device access from the lists
- Pending and active entries are shown. Removed drivers and other ended entries disappear from the primary driver list and the recipient’s shared devices. Firestore retains ended records so the camera can reject old credentials. Passed five emulator UI checks and Android unit tests. Installed on the TECNO with account, settings, and alert database preserved.
- Published D1 migration 0003, the Worker, and Firebase Hosting. Verified the web assets, a signed-in device lookup, and a Trusted Contact match without sending an invitation.

### 2026-11-25 — Immediate additional-driver access
- Add driver grants access in one owner-only demo transaction. Existing pending grants can be activated by the primary driver without a second demo event. The first authenticated Bluetooth session presents a one-time notice.
- Passed 345 Android unit tests, 11 emulator UI and storage tests, 37 Firestore rules tests, and 19 Worker tests. Candidate APK is F:/DriveAlert-direct-device-access-2026-11-25/android/app/outputs/apk/debug/app-debug.apk. Published the Firestore rules and verified their live source. Installed the matching APK on the TECNO at 10:23 PM Manila and confirmed the original account, settings, and alert database. Physical Bluetooth verification is pending.

### 2026-11-25 — Email-first additional-driver invitations
- Removed the email discovery switch in the updated Android and web clients. Exact-email lookup finds active, verified recipients eligible for the invitation. The primary driver’s device invitation starts with email, keeps the connection-code option, and shows the name and email before Send invite. Device access starts after the recipient accepts. The registered-device warning points to Settings and Subscriptions & Drivers.
- Passed 345 Android unit tests, four Android emulator UI tests, 65 web unit/component tests, five browser tests, 19 Worker tests and 35 Firestore emulator tests. The Worker dry-run and web production build passed. Signed Android candidate is F:/DriveAlert-device-invite-release-2026-11-25/android/app/outputs/apk/debug/app-debug.apk. Installed on the TECNO in place at 10:09 PM Manila. Verified the installed APK, original installation, saved account, settings, and alert database, and reopened DriveAlert. D1 migration 0003, the Worker, and the website are now in production.

### 2026-11-25 — Camera disconnect wording
- The connected camera confirmation now says **Disconnect camera?** and uses **Disconnect** for the action. All three camera connection emulator tests passed. Installed on the TECNO as an in-place update and verified the original package identity, database, and preferences directories.

### 2026-11-25 — Connected Drivers row alignment
- Centered **Disconnect** vertically beside the driver’s name, email, and phone, with the alert summary below. Passed 345 Android unit tests and three emulator tests covering alignment, confirmation and cancel, opening records, and relationship revocation. Installed in place on the TECNO and verified the signed-in account, settings, and alert database.

### 2026-11-25 — Contacts row alignment
- Moved **Remove** beside the three-line contact details and centered it vertically. All 345 Android unit tests passed. Installed on the TECNO with the account, settings, and alert database intact. Verified both rows and opened and canceled the removal confirmation.

### 2026-11-25 — Contacts and Connected Drivers
- Android shows equal **Invite** and **Sent requests** buttons and a dedicated pending-request screen. Both Android views and the web Connected Drivers list show name, email, and stored phone for approved peers. The Worker checks the approval and owner for each lookup. Pending and disconnected accounts cannot receive the number.
- Passed 345 Android unit tests, four Android emulator UI tests, 65 web unit/component tests, five browser tests, and 18 Worker tests including Firebase emulator access checks. Published the Worker and website and verified an existing approved driver’s details live. Installed the Android update on the TECNO in place and verified the signed-in account, settings, and alert database. The Huawei was unavailable.

### 2026-11-25 — Required phone numbers and simpler Account profile
- Android and web signup and profile edits in the updated source require exactly 11 digits with any prefix. The phone field no longer says optional, and the web Account page omits User role and Account status. Firebase Hosting now serves the new web build. Verified the live signup assets and signed-in Account page after deployment.
- Passed 345 Android unit tests, 65 web unit and component tests, web typecheck, lint and production build, and three browser tests with local Firebase emulators. The browser tests covered blank signup, legacy-profile editing, malformed numbers, and saving a valid number. The Android update was installed in place on the TECNO and the account, settings, and alert database were verified. The Huawei was unavailable.

### 2026-11-25 — Trusted Contact lookup by email
- The Android Driver can search for a Trusted Contact by exact email, see the name and verified email, and send a separate in-app invitation. **Find me by email** is off by default and available in Trusted Contact Settings on Android and web. The connection code option is still available.
- The Worker checks the account roles and contact consent, limits repeated searches, and rechecks the short-lived match before sending. The invite uses the Driver's Firebase token and existing Firestore rules.
- Passed 345 Android unit tests, three Android emulator tests, 65 web unit and component tests, a browser opt-in/out test, 35 Firestore rules tests, and 16 Worker tests including a Firebase emulator integration test. The Android emulator used the real HTTP client, local Worker handler, and Firebase emulators to look up, send, and accept. Deployed the dedicated read-only Firebase Auth credential, D1 migration, Worker, Firestore rules, and website on 2026-11-25. Confirmed the opt-in setting was off in the signed-in live website, and verified that the in-place TECNO update retained the account, app settings, and alert database. Full lookup and invitation verification used emulators. The Huawei was unavailable for this rollout.

### 2026-11-25 — Conditional hotspot prompt after Bluetooth setup
- Restored the **Turn on your hotspot** popup after Bluetooth setup completes, when Android reports the tethered hotspot is off. Confirmed on or unknown states keep the popup hidden. Cancel allows another Wi-Fi network.
- Android build and all three focused setup unit tests passed. Candidate is `output/DriveAlert-2026-11-25-conditional-hotspot-prompt.apk`. All four Android 35 emulator UI tests passed. The real Android hotspot was toggled, and camera Bluetooth completion was simulated. Confirmed no popup before Bluetooth completion, a popup after setup with the hotspot off, and no popup with the hotspot on. TECNO-only installation and physical validation are pending USB access.

### 2026-11-25 — Read-only Trusted Contact alert rows
- Shared alerts display the time and detected signs as plain rows on Android and on the web dashboard and driver history. The rows have no arrows, links, or tap actions. Clear history is still available, and existing direct web record links work.
- Verification passed for 61 scoped web unit/component tests, seven browser checks, 343 Android unit tests, and two Android emulator checks. Firebase Hosting version e9896e186845298c went live at 00:34 Manila. Verified all four published assets and the live history. Installed the matching APK on both phones in place and verified both original alert databases, settings directories, and signed-in accounts.

### 2026-11-25 — Simpler Trusted Contact alert records
- Web and Android now show the driver, one alert time, and detected signs. Shared history rows show the alert time and signs. Removed record type, period start/end rows, event count, session ID, delivery and sync indicators, and the repeated detail explanations. The record data and sync behavior are preserved.
- Both builds passed. Verified desktop and phone-width web layouts, 65 web unit and component tests, seven browser checks with one startup timeout passing on rerun, 343 Android unit tests, and two Android emulator checks. Installed the verified APK on both the TECNO LE6h and Huawei JLN-LX1 as in-place updates. Both retained the original installation identity, alert database, app settings, and signed-in account. Deployed the shared-alert changes to Firebase Hosting at 00:23 Manila. All four published files matched the tested Hosting build, and an existing live alert showed the three simplified fields.

### 2026-11-24 — Live Stage 3 sync to Huawei and web
- The live Stage 3 record reached Firestore at 23:56:41.321 Manila and was visible in the Trusted Contact portal by 23:56:41.603. The TECNO generated it and acknowledged upload about 1.4 seconds after queuing it. The Huawei cached the same record through DriveAlert, with a Firestore snapshot time of 23:56:41.388 Manila, about 67 ms after upload. The user saw it in the Huawei app. The Huawei was signed in as the contact and lacks Google Play services. Firebase rejected the only push attempt with an invalid token. A phone push retest requires the Huawei signed in as Driver and the TECNO registered under the approved contact account.

### 2026-11-24: latest APK installed fresh on Huawei
- Installed the actual latest tested APK, output/DriveAlert-2026-11-24-one-way-trusted-contact-invitations.apk, on the newly connected HUAWEI JLN-LX1 (serial U4YYD22408513458, Android API31) at22:01 Manila. Its SHA-256 BFA98017341F3F5B3D7F3889206C5A32AF0AF2A1ABE03F99248A25D24B5874AC matches both the current app-debug.apk and installed Huawei base.apk. User explicitly requested clearing data: targeted Huawei-only adb install -r and pm clear com.lhordkent.drivealert both returned Success. App-private directory was empty before first launch; MainActivity then opened, process23596 ran, and its crash buffer was empty. No account created or monitoring started. TECNO LE6h was connected simultaneously but was not installed to, cleared or otherwise changed. No source changes, new build/tests, cloud changes, commit or push in this delivery.

### 2026-11-24 — One-way Trusted Contact invitations
- Released the scoped portal bundle and invitation rules to Firebase production at 18:43 Manila. Live Hosting version `4ad407e440abc8e3` serves the four tested assets byte for byte. The active Firestore rules match the emulator-tested source. A signed-out browser loaded both the home and former invitation URLs and reached sign-in. Production verification was read-only.
- Installed the emulator-tested APK on the TECNO LE6h at 18:39 Manila with an in-place update. Verified the installed hash and preserved app UID, original installation date, Room database file, and preferences directory.
- Hard emulator retest used the exact candidate APK. All 11 selected Android instrumentation checks, 35 Firebase emulator tests, and two Android-to-browser checks passed. Twelve duplicate-send and accept-versus-cancel trials kept one valid outcome each. Switching off both Android networks left a legacy request intact, and re-inviting after reconnection succeeded. The retest covered the Stage 3 sharing cutoff and separate camera-driver access.
- One earlier mixed run saved the invitation on the server but timed out waiting for the Driver’s pending list. The rerun passed after the test fixture confirmed both accounts were online before timing the lists. Application code and the candidate APK were unchanged during the retest.
- In the local candidate, the Driver sends an invitation using the Trusted Contact’s code. The contact accepts or declines on Android or the web. The Driver can cancel a pending invitation, and either side can disconnect an approved relationship.
- Existing approvals keep their original dates. A fresh Driver invitation can replace an old pending request sent by the contact. Additional-driver camera invitations still use their separate workflow and codes.
- All 343 Android unit tests, 35 Firebase emulator tests, 67 workspace web unit and component tests, and 13 standard browser tests passed. Two Android instrumentation tests and two cross-platform browser tests passed. The web accepted the fresh invite sent from Android after the contact declined the previous request on Android.
- The APK is `output/DriveAlert-2026-11-24-one-way-trusted-contact-invitations.apk`. The published web bundle at `output/DriveAlert-2026-11-24-one-way-trusted-contact-web` was built from the committed web baseline plus the invitation change. All 63 unit and component tests and four connection browser tests passed on that baseline.

### 2026-11-24 — Logout progress and monitoring notifications
- Confirming logout shows a blocking **Logging out…** spinner while camera and account cleanup finishes. On the TECNO phone, a Home tap and Back did not interrupt the flow. The welcome screen appeared automatically and the existing account was signed back in.
- The Android monitoring notification shows **Paused** while reconnecting or waiting for fresh camera readings. It shows **Active** when monitoring can assess the driver. Four focused monitoring-policy unit tests passed, and the APK built successfully. The notification fix was not retested in a live camera outage.
- Installed the update as `output/DriveAlert-2026-11-24-logout-and-monitoring-status.apk` with app identity and first-install time preserved. The installed SHA-256 is `dcfc4c10155a914bee3306b445a29bde885f6daa95aac053c7b03d600f708f3c`.

### 2026-11-24: latest combined APK built and installed
- Built from the latest C:/DriveAlert working tree, combining lighting/calibration grace/view memory, mandatory exactly11-digit signup with any prefix, and the November24 hotspot-popup removal. All341 unit tests pass (0 failures/errors/skips), APK build and Android-test compilation pass. Inspected the packaged APK: phone-required error and ^[0-9]{11}$ present, lighting15/35 and2s confirmation present, automatic hotspot popup absent, saved-network action retained. Installed output/DriveAlert-2026-11-24-latest-combined.apk on TECNO LE6h with adb install -r (Success), no uninstall/data reset. Installed SHA-256 DE6EEA35B5890823BE5BB8985F6ED90C4A62731A84B9BA17B727D7BC31DED1D4 matches the candidate; UID10545 and firstInstallTime2026-11-12 21:41:49 unchanged, lastUpdateTime2026-11-24 16:00:14. MainActivity launched and app process verified. No account creation, monitoring manually started, camera accuracy/sign-up UI retest, firmware flash, web deployment, commit or push in this delivery. Private build/installation proof and source fingerprints are in ignored tmp/latest-combined-verification-2026-11-24.json.
- Updated shared project/cloud/detection/camera/functions/continuation/timeline notes. The earlier focused hotspot APK is superseded; required-number signup is now installed on Android. Website deployment remains pending.

### 2026-11-24: reconnecting during active monitoring
- On the TECNO phone with a driver in view, turning off Bluetooth alone kept fresh driver tracking and the monitoring service active.
- With the hotspot off for about 15 seconds, fresh camera readings stopped whether Bluetooth was on or off. After restoring the hotspot, fresh locked tracking and active assessment returned automatically in the same session for both outage cases. Bluetooth remained off during the second recovery.
- The Android notification said “Monitoring is active; reconnecting to the camera” while fresh camera readings were unavailable. A separate monitoring session was started for the hotspot checks after the Bluetooth-only check. Stopped monitoring after the test and left the hotspot on and Bluetooth off.

### 2026-11-24: misleading hotspot prompt fixed and retested
- Removed the automatic hotspot popup from Android Bluetooth setup. Setup still explains how to enter Wi-Fi details when needed.
- Built the Android update from the installed November 16 app baseline plus this one fix. Installed it on the TECNO phone as an update and verified its hash, app identity, original install time, and existing sign-in.
- Retested with Android reporting the hotspot on throughout setup. The saved-network button was directly available after Bluetooth connected, the popup stayed absent, and the live connection returned. The retest covered only this fault.

### 2026-11-24: real camera connection test
- Tested the installed Android app on the TECNO phone with the ESP32 camera. Video continued with phone Bluetooth off after Wi-Fi setup. A roughly 16-second hotspot outage interrupted the feed, which returned automatically after the hotspot was restored.
- After 107 seconds without the hotspot, turning it back on alone did not restore the feed. The camera appeared in Bluetooth setup and reconnected using its saved network without a power cycle or re-entering Wi-Fi details.
- The app prompted to turn on the hotspot even though Android confirmed it was on. Dismissing the prompt allowed the saved-network connection. The app was left connected over the hotspot with Bluetooth off. This test covered connectivity only.

### 2026-11-24: password reset minimum
- Firebase Authentication now requires at least 8 characters when setting a password, matching Android and web sign-up. Forced upgrades at sign-in are off.
- Verified the live Firebase policy. The emailed reset flow has yet to be tested with a test account.

### 2026-11-17: required sign-up phone number, any11 digits
- New account creation now requires exactly11 ASCII digits with any prefix (^[0-9]{11}$), after trimming surrounding whitespace. User explicitly removed the earlier09-prefix requirement. Android AuthValidation/CreateAccountScreen and AuthViewModel guard reject blank or malformed numbers before Firebase account creation. Web registration form/schema and authRepository.register use the same required rule via registrationPhoneSchema. Existing form fields only; no popup/new screen. Profile editing, peer contact display, Firestore rules and existing account data were not changed.
- Fresh341 Android unit tests pass (0 failures/errors/skips), assembleDebug and compileDebugAndroidTestSources succeed. Web check passes typecheck, lint, all78 unit/component tests and production build. Regression coverage includes blank/10/12-digit/nondigit values, valid non09 prefixes, form submission blocking, direct auth guard and trimmed pending-profile persistence without password storage. Updated native UI and browser emulator fixtures; instrumentation/browser end-to-end tests were not run.
- Candidate output/DriveAlert-2026-11-17-required-phone.apk built, not installed; website production bundle built, not deployed. Phone still has2026-11-16 view-memory/calibration-grace APK. No new commit/push or live account creation. Firebase deployment requires the existing ask-first project rule. Shared Claude project/cloud/functions/continuation/timeline notes updated.

### 2026-11-16: plain-language PDF reference guides
- Created matching monitoring (3 pages) and full tech-stack (4 pages) PDFs in `output/pdf/`. Covered thresholds, timing, stages, visibility, limitations, technologies and their roles. Monitoring includes current15/35 lighting thresholds,2s poor confirmation +3s lighting-warning qualification and2s calibration missing-lighting grace.
- Rendered and visually checked every page; verified page counts, readable text and content bounds. This was document work only: no monitoring behavior changed or new app/hardware tests performed.

### 2026-11-16: authorized main checkpoint
- User authorized committing and pushing all pending source, tests, tools and documentation to main. This checkpoint includes lighting tolerance, existing face-unavailable attribution, bounded calibration lighting grace and per-account Driver/Trusted Contact view memory.
- Latest code verification remains340 passing unit tests, successful APK build and Android-test compilation; the same build is installed on TECNO with data preserved. Real-camera and login UI/stress checks remain pending.
- APKs, screenshots, raw camera captures, session logs and generated firmware outputs remain local, as previously requested. Shared Obsidian memory records the resulting commit after push verification.

### 2026-11-16: calibration lighting tolerance and last-view memory installed
- Calibration bridges absent lighting for up to2s since the last admitted usable sample while fresh (<=1s) locked/aligned face measurements continue. Actual poor lighting/recovery, face loss, target/alignment/connection changes and camera stalls cancel grace. Initial admission/phase start still require fresh usable light; missing facial measurements never advance calibration. Monitoring freshness and warning timings remain unchanged.
- Root cause of returning BOTH accounts resetting to Driver: every profile snapshot defaulted to Driver and no last view was saved. PostAuthViewModel now saves the selected view per Firebase UID in local SharedPreferences. Login/profile refresh restore it; sign-out retains it; accounts stay separate. BOTH retains its capability meaning. Select the desired view once after this update; previous unsaved choices cannot be recovered. Preference is per phone, not cloud synchronized.
- All340 Android unit tests pass (0 failures/errors/skips); assembleDebug and compileDebugAndroidTestSources pass. Installed output/DriveAlert-2026-11-16-view-memory-calibration-grace.apk on TECNO LE6h using adb install -r (Success). UID10545 and first-install time2026-11-12 21:41:49 unchanged. Installed SHA-256 matches36C035E8D8B8EC4A911C206667B477240031CD163287C1993B63A38AD8412925; MainActivity reopened and process verified.
- Shared Claude project/detection/cloud/functions/continuation/timeline notes updated. Real-camera dim-cabin/calibration/audio and device login UI/stress checks remain pending. No synthetic records written to real accounts/databases, monitoring manually started, firmware flashed, cloud change or new commit/push. Eye investigation remains stopped.

### 2026-11-16: lighting update installed on TECNO with data preserved
- Installed output/DriveAlert-2026-11-16-lighting-tolerance.apk on TECNO LE6h (080633723R000519) using adb install -r; installer returned Success. No uninstall or app-data clearing. Existing Android app UID and first-install time remained unchanged; installed base.apk SHA-256 matches the tested candidate.
- Reopened MainActivity and verified the app process is running. Updated the shared Claude project, detection, warning, function, continuation and timeline notes. This verifies installation/basic launch, not real-camera lighting accuracy, nod handling, UI/stress coverage or audible timing. No monitoring session was manually started, firmware flashed, cloud change or commit/push performed.

### 2026-11-16: bounded follow-up verification; emulator run blocked
- User requested stronger testing, then asked to do it quickly. Reviewed the lighting/controller/runtime changes and freshly packaged assembleDebugAndroidTest successfully. Previous final331 unit tests and app build remain passing; production source unchanged since that run.
- Attempted a separate disposable API35 emulator for monitoring/calibration screen checks. Startup was blocked by insufficient disk space (about4GB available; image required more than7GB even after reducing requested partition size). No instrumentation/UI test executed in this follow-up, and no monkey/stress test or real-camera accuracy check performed. A physical phone appeared in ADB but was not installed to or used. Stopped additional setup to respect the time constraint; no claim of complete hardware regression validation.

### 2026-11-16: lighting tuning implemented locally; candidate APK built
- User requested implementation after lowering proposed brightness cutoffs another5 points. Local code now uses15/35 with contrast20, immediate assessment pause,2s poor-light confirmation plus3s lighting-warning qualification, quick return after an unconfirmed dip and2s recovery after confirmed darkness. Sampling/freshness remain unchanged.
- Removed ambient-darkness overrides of existing face loss; original face alert3s timing/text/track/recovery remain. Lighting qualification is independent of face-loss time; queued lighting output revalidates fresh same-interval evidence. UI/service status handles checking/recovery without stale lighting attribution.
- Fresh final verification: all331 Android unit tests pass (0 failures/skips), assembleDebug and compileDebugAndroidTestSources pass. Regression coverage includes cutoffs, brief dips,5s qualification, independent face/light clocks, cache/gap handling, recovery, queued-speech eligibility and calibration baseline exclusion. Existing face3s/recovery/reminder tests remain passing; the screen test now expects face loss in dim scenes and separately checks selected-face lighting states.
- Initial focused iterations exposed obsolete assertions; a later compile hit Java native-memory exhaustion. Final build passed with command-only single-worker/in-process compilation and a lower JVM heap, without project/version changes. Built output/DriveAlert-2026-11-16-lighting-tolerance.apk. No ADB device connected: no phone install or current camera/audio/UI execution. Actual dim-cabin accuracy/timing still needs a parked-camera test. No firmware/cloud change or new commit/push; shared project notes updated.

### 2026-11-16: lighting proposal expanded; existing face alert unchanged
- User requested brightness cutoffs another 5 points below the previous proposal: severe-dark 15 and dim/weak-contrast 35, with contrast 20 unchanged. Updated plan examples and boundary cases. Proposal only; production thresholds remain 25/45 and warning timings are unchanged.
- Expanded LIGHTING_TUNING_PLAN.md with candidate thresholds, startup/calibration handling, lighting confirmation/recovery timelines, code inspection points and automated/parked-camera acceptance cases. Documentation only; no runtime changes or new tests/build/install.
- User clarified the existing face-not-visible alert already works. Removed the earlier proposal to increase its delay: current 3s delay, recovery, speech and reminders stay in place. Only the ambient-dark override and lighting-specific qualification are proposed for change. Lighting time must not inherit missing-face time from the controller's shared candidate clock.
- Checkpoint96fa8ca2 is pushed to main. This plan revision is local and has not been committed/pushed.

### 2026-11-16: save existing work to main; lighting change remains a proposal
- User authorized committing/pushing existing source, tests, tools and documentation to main. APKs, phone images, raw session exports and generated firmware outputs stay local. All old local feature-branch commits are already in main; no additional branch merge is needed.
- Reviewed the pending calibration recovery, speaker timing/protection, visibility episode, eye diagnostic/safeguard and setup-dialog changes. Fresh checks: 315 Android unit tests pass with no skips; assembleDebug and compileDebugAndroidTestSources pass on JDK17; firmware warning-output host test passes. Initial sandboxed Android check failed on SDK file access; the approved rerun passes. No new physical-phone/camera or live FCM test performed.
- LIGHTING_TUNING_PLAN.md records the proposed fast sampling, five-second warning confirmation, correct missing-face attribution and provisional dim-light threshold trial. No lighting implementation, threshold change, phone installation, firmware flash or cloud deployment in this step. Earlier trial edits were undone at the user's request. Eye-gaze investigation remains stopped.

### 2026-11-15: only the two specified accessory choices removed
- User clarified with screenshots: remove Ordinary eyeglasses and Mask and sunglasses only. No obstruction, Face mask and Sunglasses remain selectable. A static notice now says covering both eyes and mouth is not allowed and obstructions must be removed before monitoring.
- Automatic obstruction/pausing behavior unchanged; old enum values remain readable for persisted session recovery. This is a dialog change, not a detector fix. Earlier setup disclaimer removal remains in place.
- assembleDebug and targeted whitespace check pass. Installed output/DriveAlert-2026-11-15-three-accessory-choices.apk preserving data and reopened app. Rendered dialog verification could not proceed because home was not ready; no monitoring session started. No new tests for this simple UI change, firmware flash, commit/push/deploy or data reset. Eye investigation remains stopped.

### 2026-11-15: requested Setup & Device notice removed entirely
- Deleted the screenshot's entire selected-visible-signs/no-diagnosis card, including container, check icon, copy and dedicated spacer, from SetupDeviceContent. No replacement text or banner.
- assembleDebug and targeted diff whitespace check pass. Installed output/DriveAlert-2026-11-15-remove-setup-notice.apk as an update preserving phone data, then reopened the app. No new tests for this simple UI deletion.
- Eye investigation stays stopped. Detection, calibration and firmware unchanged; no commit/push/deploy or app-data reset.

### 2026-11-15: user stops eye investigation; disclose limitation during defense
- User explicitly stopped troubleshooting for now. No more research, code changes, installs, captures or pose tests unless resumed. Numeric collectors were already stopped. Current raw-eye-diagnostic APK stays installed; no detector fix claimed.
- Camera position varies by driver; the current mount cannot be moved for the proposed eye-level comparison. Fixed eye-level placement is not an accepted product requirement.
- Defense statement: Reliable distinction between downward gaze and actual eye closure across different camera positions is outside the prototype's validated scope. False eye-closure alerts under these conditions remain a known limitation and future improvement.
- Internet research identified candidate eye-image classification and 3D geometry approaches; none was implemented or validated as a fix. Do not represent these suggestions or passing software tests as detection-accuracy validation.

### 2026-11-15: paired EAR/gaze test confirms overlap; candidate agreement rule rejected
- User confirms lens was below eye level. Low viewpoint is a candidate contributor, not a confirmed cause. Next proposed parked comparison temporarily raises camera nearer eye level while keeping distance/light similar, monitoring stopped and calibration unchanged. No collector or pose hold started.
- User completed ahead/open, down/open and gentle closure after separate cues. Numeric raw EAR and eye scores now pair correctly. Monitoring stayed stopped; preview CONFIRMED is not a delivered alarm. Cue-to-reply windows include reading delay/transitions/recovery, not exact continuous pose labels.
- Ahead cue window EAR median0.248 with no CONFIRMED; down cue median0.136 and repeated CONFIRMED states at near-neutral relative pitch. Plausible closure response15:47:37–45.7 has EAR roughly0.125–0.184, overlapping down. Lowering EAR cutoff is unsupported. Blink/look-down scores overlap too.
- Simplified logged-sample replay: requiring both eye-open scores<=.20 continuously2s rejects current down and preserves current candidate closure, but misses the earlier user-confirmed14:54:45 closure. Looser .50/.80 still confirms down and misses that prior closure; .85 detects it but also down. Rejected candidate; no detector/settings/install changes. This is not exact production replay or accuracy validation.
- Collector stopped and no leftovers found; user relaxed, no more pose repetitions requested. Asked camera height/viewpoint only. No images/firmware/commit/deploy. Evidence: output/Eye-gaze-paired-review-2026-11-15.md, output/eye-gaze-paired-summary-2026-11-15.json. False-alert fix remains unresolved.

### 2026-11-15: raw EAR diagnostic correction installed and verified live
- Previous preview comparison withheld normal EAR/MAR/pitch. Debug expression line now includes selected-current-face raw EAR/pitch before assessment gates. DriveAlertEyes separately logs source timestamp, alignment, face lock, lighting eligibility and monitoring activity. No gate/threshold/calibration/alert behavior changed; no images captured.
- assembleDebug passes; targeted diff whitespace check passes. First install found no USB device; user reconnected and update install succeeded with data kept. Latest installed APK: output/DriveAlert-2026-11-15-raw-eye-diagnostic.apk. Normal app reopened. No test helper installed.
- User restored preview. New numeric raw EAR and gate context match by source timestamp. Sample shows alignment=true, lighting UNKNOWN withholding assessment, then USABLE admitting numeric EAR; monitoring=false. This explains those new gated samples, not every earlier uninstrumented sample. Natural posture was not labeled, so do not classify low readings as a confirmed false event. Saved output/raw-eye-diagnostic-verification-2026-11-15.log. No active collector or further timed pose requested.
- Previous 315 unit tests precede these logging-only edits and were not rerun. Downward-gaze fix remains unresolved; raw geometry does not bypass assessment, and expression scores have not reliably separated closure. No firmware flash, commit, push or deployment.

### 2026-11-15: numeric gaze comparison completed; simple new score cutoff not supported
- User completed no-mask/glasses ahead/open (normal blinking), down/open, then gentle4s closure with monitoring stopped. Selected-face blink/look-down scores were recorded; no images. Cue-to-reply windows include transition/recovery and are not exact continuous pose labels.
- Initial ahead/open blink ranges .122–.237/.065–.264; down cue interval peaks .708/.718; closed cue interval peaks .509/.745. Look-down activation was already .58–.81 in the initial ahead/open sample and reached ~.89 in both other intervals. These overlapping scores do not validate a simple gaze/blink cutoff; a look-down veto could suppress actual closure.
- Normal EAR/MAR/pitch were null/UNAVAILABLE for all237samples; the exact assessment gate was not established. This run validates expression logging, not actual monitoring alerts or synchronized EAR separation. Earlier downward-gaze EAR reproduction remains separate evidence.
- Collection stopped after final done; no further pose requested or live collector left. No detector/settings/install/firmware change. Fix remains unresolved. Detailed evidence: output/Eye-gaze-diagnostic-2026-11-15.md and output/eye-gaze-phase-summary-2026-11-15.json.

### 2026-11-15: downward-gaze error reproduced; numeric diagnostic installed
- User-labeled downward gaze produced EAR 0.12–0.18 below the saved 0.284 threshold, reaching preview CONFIRMED with near-neutral head pitch. This was a stopped-session preview, not a newly delivered alarm. Looking ahead afterward had median EAR 0.317 and no CONFIRMED state in the sampled recovery window. A head-pitch-only exclusion would miss this case.
- Calibration stores closed-eye EAR about 0.052, but the current threshold uses neutral EAR ×0.75 only. No threshold change is justified by this small sample; a lower threshold could miss genuine closure in other conditions.
- Enabled debug-only MediaPipe blink/look-down/squint numeric logging for the selected current face. The bundled task already contains its blendshape model. Scores do not affect alarms/calibration and no automatic image capture was added. Google's model card documents shared-landmark limitations; gaze-versus-closure separation on this camera is still unverified.
- 315 unit tests/build pass; targeted native empty-view test passes on TECNO. Installed output/DriveAlert-2026-11-15-eye-expression-diagnostic.apk with app data kept. Live numeric camera check could not reach the saved camera or discover it; no frames/scores collected. Helper removed and normal app reopened. User will reconnect later; no active collector or pose test. Final instrumentation build passes after strengthening expression-output checks. This is a diagnostic update, not a completed false-alert fix. No firmware flash, commit, push or deployment.

### 2026-11-15: Android emulator shared-record and notification test passes
- Added IT-09 to SoftwareIntegrationAuditTest and ran it on isolated emulator-5556 with local Firebase Auth/Firestore (9195/8185). Real Room -> WorkManager -> Firestore -> approved-contact mobile list/detail passed. Notification handler posted the expected title/body to Android's active notification list on the HIGH importance urgent channel with a tap intent; wrong-recipient message was ignored. One instrumentation test passed (29.197 s), no skips.
- Message was injected directly into DriveAlertMessagingService.onMessageReceived; Google FCM transport, live server credentials/device registration, real phone delivery and audible output were NOT exercised. No claim of live end-to-end push delivery.
- Test APK built with an empty worker URL to prevent production dispatch; keep this test build off user phones. Normal generated configuration restored afterwards. Production app behavior unchanged. No live accounts/records/notifications created. Isolated test services shut down after verification.
### 2026-11-15: Level 3 trusted-contact delivery checks pass locally; real phone receipt pending
- Fresh focused Android run: 36 tests pass (24 warning-stage, 9 sync foundation, 3 notification dispatcher). Worker TypeScript check and all 10 tests pass; dispatch tests use mocked Firebase/FCM, so they do not prove live push delivery. Firebase emulator rules: all 32 tests pass, including approved-contact access, pre-approval denial and revocation.
- Current generated Android configuration contains the notification-worker endpoint. Website provides shared records but no browser push; Android push requires an approved connection, a registered signed-in contact device and allowed notifications. Cloud upload/server completion is not a handset receipt acknowledgement.
- ADB reports no connected phone. No real notification sent, production data inserted, accounts created, app behavior changed or code deployed. Real verification still needs an actual Level 3 event while stationary, matching record on the approved contact account, and visible notification on their Android phone.
### 2026-11-13: eye safeguard retest partially passes; masked open-eye false alerts remain
- User reports no-mask/open20s passes; masked eyes-open20s still intermittently alarms. Masked deliberate4s closure and repeat after10s both alert as expected. These two trials preserve observed closure detection, not general accuracy certification.
- Current logs/persisted events saved. Several remaining low-EAR runs include .87–.89 open scores, below the .90 veto; exact test-phase timestamps are not labeled. No unsupported cutoff/timing adjustment or new install performed. Remaining work needs raw evidence matched to an actual masked false event.

### 2026-11-13: partial safeguard for mask-sensitive eye readings installed
- New 22:04 logs show six eye events; several candidates include one strongly open eye that the previous both-open rule ignored. User clarifies friend's open/closed EAR responds normally and mask changes little; no frozen-display report.
- Fresh available/unobstructed valid evidence from either eye >=0.90 now breaks bilateral-closure timing. EAR/calibration/2 s timing remain unchanged. Modeled mask/asymmetric replay and validity boundary checks added. Both-model errors can still trigger false warnings or suppress real closure; accuracy not certified.
- 315 unit tests pass; APK/instrumentation compile pass. Installed output/DriveAlert-2026-11-13-eye-conflict-guard.apk preserving data and reopened app. Physical masked closure/glasses retest pending. No firmware flash/commit/push/deploy in this step.

### 2026-11-13: latest eye Monitoring Logs inspected
- Saved monitoring records confirm actual eye-closure detections at 20:56:08 and 20:57:37, durations 2.008/2.376 s, both Stage1; second repeated about3s later. No logging gaps in those sessions.
- Fresh classifier readings include mixed/one-sided open evidence; current veto requires both eyes >=0.90 and does not fire during either qualifying sequence. Low EAR therefore confirms. Software trigger established; actual eyelid state/glasses not proven by scores. User context requested; no sensitivity or code change.
- Selected identifier-free event export and detailed local report saved; transient full DB snapshot removed. See output/Eye-monitoring-review-2026-11-13.md.

### 2026-11-13: mask popup/announcement loop corrected; classifier instability remains
- Mask-startup logs show locked-face mouth/both-region speaker commands 3–5 s apart as eye-obstruction classification fluctuates. Runtime dismissed popup on any issue change; controller restarted speech cadence. Both behaviors corrected.
- Same continuous episode retains its pending popup and 30 s speech cadence; new attribution still confirms for 3 s and can update prompt silently. Genuine recovery still clears after 2 s; acknowledgement never bypasses detection gates. Dialog clarifies face/light loss pauses monitoring.
- All 311 unit tests pass, APK/instrumentation compilation passes. Installed output/DriveAlert-2026-11-13-visibility-episode.apk preserving data and reopened app. Real popup retest pending. Actual classifier swings/masked face-loss not solved; no firmware change/flash or commit/push/deploy.

### 2026-11-13: original inputs captured; historical false warning not reproduced
- Native opt-in capture passed for 5 and 20 natural frames. Full images, landmarks and 100 Android-prepared crop inputs saved only locally for offline inspection. Example crops contain the full face and normal-looking colour.
- Offline replay matches all 100 native mask scores exactly. Current 0.20-margin scores stayed at 0.000–0.176, below 0.80. These healthy samples do not explain or disprove historical false warnings; failing original inputs are missing. Synthetic detail loss also does not establish a resolution fix.
- Added bounded diagnosticSamples (1..20, default5) and reusable replay script. Diagnostic builds/native runs pass; 307 prior unit tests pass. No production detection-setting change. Removed temporary phone captures/helper; normal app reopened; computer evidence excluded from Git. No ongoing image recording.

### 2026-11-13: USB restored; camera connection blocks original-input capture
- Opt-in native diagnostic now supports original frame/landmark and Android-prepared input export via exportModelInputs=true; default saves no images. Production inference omits its synchronous observer and keeps existing settings.
- 307 unit tests pass, app/instrumentation builds pass, diagnostic-compatible update installed preserving data. Saved camera address timed out; named-camera discovery also failed. No images collected. Normal app reopened; waiting only for camera live video restoration, no pose test.

### 2026-11-13: offline diagnosis available; original camera input still needed
- Added scripts/face_attribute_probe.py with usage notes: bundled-model inference, crop images and scores entirely offline. Smoke check passed and asset hash/quantization match. Pillow resize/manual bounds are approximations.
- Screenshot inspection shows modal overlay contaminates its padded face crop; it cannot establish raw-camera accuracy. No production detection change. ADB currently has no connected phone, preventing fresh native capture/retest. No further pose test requested.

### 2026-11-13: uncovered-mouth false warnings confirmed; crop diagnosis in progress
- User reported two mouth-blocked false positives while looking ahead, uncovered. Their phone screenshot confirms visible mouth plus the obstruction dialog. High mask scores persisted with tracking locked; this is not fixed by calling it a short spike.
- Production crop/threshold policy is unchanged. Optional classifier padding supports a guarded live diagnostic, skipped by normal test runs. On one same-frame uncovered-face comparison: padding 0.0 gave mask 0.324, 0.1 gave 0.520, current 0.2 gave 0.902, 0.3 gave 0.984. More clear frames and a positive covered-mouth control are needed before selecting a change.
- Native diagnostic initially failed due to its own premature MPImage closure; that test bug is corrected and the single-phase run passed. After reconnection, paired comparison completed: five clear and five covered frames; uncovered scores reached 0.996 at every padding, while covered scores stayed 0.996. Tighter cropping alone is not a reliable fix. Helper removed and normal app reopened. User clarified that hand and mask are both expected obstruction cases; false warnings with an uncovered mouth are the defect. No diagnostic face images persisted. Existing user screenshot saved at their request.
- Build and 307 unit tests pass. Do not consider mouth/glasses/extreme-down-gaze behavior validated. No firmware/production-mouth change, commit, push or deployment in this step.

### 2026-11-13: downward-gaze eye check installed; yawn onset being measured
- **Confirmed false eye warning:** at 19:07:44, EAR was 0.184 against threshold 0.246 while independent eye-open scores were 0.980/0.953. User says merely looking down to type triggers it.
- **Change:** fresh available/unobstructed classifier evidence with both eye-open outputs >=0.90 and age <=750 ms now rearms eye-closure timing. Stale, unknown or one-sided readings cannot veto closure. Calibration thresholds, 2 s genuine closure and yawn/head rules remain unchanged.
- **Verified:** 307 unit tests pass (including down-gaze replay then genuine closure, stale/one-sided evidence and unaffected other detectors); APK/instrumentation sources build. Installed `output/DriveAlert-2026-11-13-eye-gaze-crosscheck.apk`, preserving TECNO app data. Actual down-gaze/closure/glasses retest still required.
- **Mouth:** user now reports blocking works in this test. Stable obstruction needs 3 s after classifier recognition (typically 4–5 s total); voice waits if Stage 3 is speaking. Yawn warning onset feels slow: confirmation stays 3 s, and MAR/duration/head/event logs were added to identify additional delay. No firmware changes, commit, push or deployment in this step.

### 2026-11-13: speech cutoffs and premature pauses corrected in source; false eye alerts still open
- **New user reports:** roughly four sounds then a pause; Stage 3 speech cut off by another warning; mouth-blocked detection only partly reliable; eye false positives much worse with ordinary glasses.
- **Evidence/fixes:** Stage 3 requests 3–5 s apart were restarting the mixed track. App protection now coalesces warnings while that track's 10.3 s reservation is active and delays visibility speech, preserving event/history accounting. Camera firmware now protects pending/playing Stage 3 speech inside its existing 10 s guard too (source change, NOT flashed).
- **Pause evidence/fix:** valid ~450 ms inference plus ~600 ms sampling made a capture-age watchdog clear evidence just before a valid result. Capture-age admission and lighting/face checks remain strict; pipeline silence now measures elapsed time since a valid result completed. Old UI readings are hidden immediately without unnecessarily resetting temporal detectors during ongoing inference.
- **Verified:** 304 Android unit tests pass; app and instrumentation sources build. Firmware host tests pass including attempted interruption of queued/playing Stage 3. ESP32-CAM target firmware build passed (core 3.3.12, 59% flash / 27% globals); ready files in `output/stage3-speech-firmware/`, NOT flashed. Final app update is installed on TECNO with data preserved. New APK: `output/DriveAlert-2026-11-13-protected-speech-eye-diagnostics.apk`.
- **Still open:** actual audible protection/continuity retest; consistent mouth recognition; eye false positives with glasses. Added EAR/threshold/eye-open-probability diagnostics; no sensitivity retuning without the controlled eyes-open sample. User has not performed that diagnostic test yet. No firmware flashed, commit, push or deployment.

### 2026-11-13: Codex continuation — combined update installed; camera retest pending
- **Mouth test evidence:** normal sitting distance still caused face loss, but moving closer produced the "mouth blocked" message (confirmed by the user) and stable 0.996 mask readings. The detector's face scale/continuity is implicated; changing confidence alone did not fix it.
- **Recovery change:** a second single-face VIDEO tracker processes each current full frame, retaining MediaPipe's internal face-crop tracking. It supplements the multi-face graph only when that graph returns zero faces. Existing competing-face, driver-area, continuity, acquisition and freshness checks still apply. Both graphs preserve full-image geometry; no stale landmarks or face images are stored.
- **Alarm timing:** repeats are spaced from actual requests and camera acceptance: at least 2.3 seconds for ordinary alarms, 10.3 seconds for Stage 3 mixed speech and 4.3 seconds for visibility speech. Pending output requests suppress overlapping reminders. Late replies from an old session cannot delay the new session.
- **Verified:** 300 unit tests pass; APK/instrumentation build succeeds; the native two-tracker empty-view test passes on the TECNO (one specifically selected test; no app-data resets/accounts/Room writes). The test helper was removed afterwards.
- **Installed:** `output/DriveAlert-2026-11-13-face-recovery-alarm-timing.apk`, as an update preserving TECNO app data. **Still pending:** normal-distance mouth test and audible alarm-rhythm retest. No firmware flashed, commit, push or deployment.

### 🔊 2026-11-13: no sound from the camera speaker (being investigated)
- **What we saw:** covering your mouth showed "Limited monitoring" but made no sound. The app's own log shows most speaker requests to the camera got **no answer within 4 seconds** (only 1 of 5 drowsiness alarms went through). When the camera isn't streaming video it answers in about 45 ms, so we think it gets too busy while sending video.
- **Crash found and fixed:** the app crashed if monitoring stopped while an alarm was still waiting on the camera. Fixed; unit tests pass.
- **Added:** the app now logs how each speaker request went (worked/failed, how long it took).
- **Cause found (test run 17:34):** before every sound request the app checked the camera by connecting to its *video* port. While video streams, that port never accepts new connections, so the checks piled up. Later requests waited 1.5–4 s and failed, which gave random gaps (only ~half of alarms got through).
- **Fix (app only):** the check now uses the camera's free control port. **Retested 17:41: works.** Nearly every beep reaches the camera in under 0.1 s, and the user confirms eyes-closed now always sounds. A few repeats are still rejected as "busy" because the app's 2 s repeat collides with the camera's 2 s guard; a fix is optional (repeat about every 2.3 s).
- **Mouth cover said "face not visible":** a hand over the mouth made the face detector drop the whole face (0 faces seen). Lowered the face detector's confidence settings from 0.5 to 0.3 so a half-covered face is still tracked. Installed and **retested: not fixed.** At 17:47 on 2026-11-13 the user still reported "face not visible" and no phone pop-up. Codex recovered that final report from the Claude chat; investigation remains open. Saved phone logs also show zero-face results and accepted FACE_UNAVAILABLE sound commands (52–74 ms), so request delivery alone does not prove mouth classification or audible playback.
- **By design, not a bug:** eyes closed repeats the beep every 2 s. Stage 3 plays a longer sound + spoken advice, and the camera won't start a repeat for 10 s after it (an estimated track length, not measured).
- Covering the mouth showing "Limited monitoring" and pausing only yawn checks is by design (like a mask). The missing mouth sound was most likely the same connection problem.

### ✂️ 2026-11-13: calibration instructions cut way down
- The step pop-up went from about 90 words to two short lines (what to do, then "Tap Okay, wait for the countdown, then hold until you hear the ping"). The page intro, "step done" messages and the redo pop-up are shorter too.
- Build and unit tests pass, and the on-phone test files still compile. Installed on the TECNO phone. The on-phone screen tests weren't run, because running them would wipe the app's data on your phone.

### 🔧 2026-11-13: calibration no longer starts over when you step out of view
- **Problem:** during calibration, leaving the camera's view and coming back sent you back to step 1 (normal face).
- **Fix:** steps you already finished are now kept. Only the step you were on is repeated, and the app shows "Face lost… repeat this step". (Changing the camera's driver area or reconnecting the camera still restarts calibration, on purpose.)
- **Tested:** all Android unit tests pass, including a new test for this. **Not yet tried on the real camera.**
- **New install file:** `output/DriveAlert-2026-11-13-calibration-keeps-steps.apk`. **Installed on the TECNO test phone (2026-11-13)** as an update, so app data and sign-in are kept. Not saved to GitHub yet.

**In one line:** Everything we can test without the camera passes. The three bugs most likely to hurt real users are fixed. A few smaller or unlikely issues are written down but not fixed yet.

### 🧪 2026-11-12: "extreme" test run (everything that doesn't need the camera)
Ran every test suite more than once, plus a random-tap stress test. Nothing touched the live website or real database.

| What | Result |
| --- | --- |
| Android unit tests (294) + lint | ✅ 4 runs, 0 test failures (one run returned a build-tool error with no failed test) |
| On-phone tests on emulator (92, incl. accounts & connections on a local test database) | ✅ 2 runs, 0 failures (1 skipped: needs the real camera over Bluetooth) |
| Database security rules (32) | ✅ 3 runs, all pass |
| Website: type check, lint, 74 unit tests, build | ✅ passes; unit tests 3/3 runs |
| Website browser tests (14 + 3 skipped) | 🟡 passes when the PC is quiet; flaky under heavy load (timeouts) |
| Alert server (10) | ✅ pass |
| Camera firmware logic (3 suites) | ✅ 5 runs each with strict crash checks on |
| Random-tap stress (3 × 3,000 events, phone offline) | 🟡 1 crash + 1 freeze, neither repeated on replay |

**Found:**
1. **Rare crash when typing:** a bug inside Android's text-box library (Compose), not DriveAlert's own code. It needs very fast random tapping to trigger. **Checked 2026-11-12: updating does NOT fix it.** The same faulty line is still there in Compose 1.7.0, 1.7.8 (the newest that fits our version locks) and 1.8.3. So we're staying on BOM 2024.02.02 and accepting this as a known rare issue.
2. **One freeze** during stress testing on the slow software-graphics emulator. It didn't repeat and is probably the emulator, but that isn't proven.
3. **Flaky website test: FIXED (2026-11-12).** The cross-browser sign-up test now waits up to 10 seconds for the verification email instead of checking once. It passed 10/10 times, and the full browser suite then passed twice more (14 passed, 3 skipped by design). Only the test changed; the website didn't. (One earlier run failed because the PC ran out of memory while the emulator was open, which wasn't a test problem.)

Note: Steam uses port 8080 on this PC, so the local database tests were run on port 8085 using a temporary settings copy (deleted after).

### 🆕 2026-11-12: faster camera video start (phone app only, saved to GitHub; APK file kept local)
- **Problem:** after setup, the video took about 30 seconds to appear. The camera restarts into Wi-Fi mode, and during that time the phone was doing slow 8-second network searches and waiting longer and longer between tries.
- **Fix:** right after setup (within 1 minute), the phone now checks the camera's known address about every 0.75 seconds for up to 25 seconds. If that doesn't work, it falls back to the old search. The camera doesn't need reflashing.
- **Tests:** all 294 app tests pass, including 3 new ones for this. New install file: `output/DriveAlert-2026-11-12-faster-camera-start.apk`.
- **Installed on the real phone (TECNO LE6h, Android 11) on 2026-11-12:** the old app and all its data were removed first, so it starts fresh. The welcome, Sign In and Create Account screens open without crashing, and empty sign-in boxes show "required" errors. No account was created on the phone.
- **Not checked yet:** on the real camera. Expect the video in about 8–12 seconds. If it gets worse, reinstall `output/DriveAlert-2026-11-11-fixes.apk`.

### ✅ Done
- **Alerts getting stuck (F1): fixed and live.** One failed alert used to stop all of a driver's later Stage 3 alerts from reaching their contacts. Fixed in the app and in the alert server, and the server change is deployed.
- **Phone stuck "Starting monitoring" (F4): fixed.** If something changed while monitoring was starting up, the phone could keep a notification and drain the battery. It now stops cleanly and shows why.
- **Full test run: all green.** That covers the Android app, the web portal, the database security rules, the alert server and the camera firmware logic (on the PC, not the device).
- **Outdated test: fixed.** One test expected old on-screen wording; it now matches the app.
- **Everything above is saved to GitHub.**
- **Website is live with the latest fixes (2026-11-11).** https://drivealert.web.app now has the sign-up fix (F5) and the sharing notice. The live sign-in page loads with no errors. Full sign-up wasn't re-tested on the live site, to avoid creating a real account.
- **Phone app: new APK built (2026-11-11): `output/DriveAlert-2026-11-11-fixes.apk`.** It includes F1 (app side), F4 and the sharing notice, and it's set to use the live alert server. It installed and opened on a test phone (emulator) with no crashes. Phones get these fixes once this APK is installed on them.
- **Website sign-up across browsers (F5): fixed (saved to GitHub).** Signing up in one browser (for example Chrome) and then confirming and logging in from another (for example Brave) used to end on "Profile not found". Now the website builds the profile from the account's name and email. **Tested with real Chrome and real Brave:** the old code showed "Profile not found", and the new code reaches the Dashboard (passed 3 times in a row). Signing up and finishing in the same browser still keeps every detail. One limit: in the cross-browser case the phone number isn't carried over, so the user adds it later on the Account page.
- **New: sharing notice on connection requests (saved to GitHub).** Before sending a request, the phone app and the website now say: "Your name (…) and email (…) will be visible to the person you send this request to." Tests pass, but it hasn't been checked on screen.

### 🟡 Known but not fixed yet (in order of importance)
1. **F2 (lowered to Low–Medium):** a connection request shows the sender's name and email, but the sender's own app supplies them, so a tampered app could fake them. It only works for someone who already has your connection code. **Decision (2026-11-11):** for now we only added a notice telling senders what's shared. The behind-the-scenes block on faked emails was skipped on purpose.
2. **F3 (left for the future, decided 2026-11-11):** a stranger near the camera could register it online before you do. They still can't use the camera without its 6-digit pairing PIN; they could only block online features such as adding drivers. Low risk, so it's not being fixed now.
3. **Smaller items (F6–F11):** alert times shown in UTC, error messages that disappear too fast, and similar.

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

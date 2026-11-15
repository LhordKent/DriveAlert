# DriveAlert System Audit — 2026-11-11

## Current status (plain English) — last updated 2026-11-16

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

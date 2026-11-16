# Lighting warning tuning plan — 2026-11-16

Status: implemented locally at the user's request. Cutoffs15/35 with contrast20,
two-second poor confirmation, independent lighting qualification and same-episode
speech revalidation pass340 unit tests, including the later calibration-grace
and view-memory follow-up. APK build and Android-test compilation pass. Installed
output/DriveAlert-2026-11-16-view-memory-calibration-grace.apk on the TECNO
phone as a data-preserving update; app identity unchanged and APK hash verified.
Physical dim-cabin/camera/audio validation is still pending.
Existing work was pushed to main in commit96fa8ca2 before this local revision.
Correction: the existing face-not-visible alert, its three-second delay, recovery
and speech stay unchanged. The earlier five-second face-alert proposal is removed.

## 1. Goal and reported failure

Allow a slightly darker but still usable face view, and avoid lighting speech
for brief shadows or head movements. Keep measurements responsive.

The user reports that a driver's face is visible under taxi cabin light, but a
nod exposing mostly hair triggers insufficient lighting. The current rule turns
missing face plus a dim-scene hint into insufficient lighting. That selects the
wrong existing alert when face position is the problem. This is a plausible code
path for the report; the actual taxi event has not been logged or replayed.

## 2. Settings

| Setting | Before this change | Implemented candidate |
| --- | --- | --- |
| Facial-light sampling | At most every 200ms, subject to processed frames | Keep |
| Lighting sample freshness | 1 second | Keep |
| Initial usable-light confirmation | 2 seconds | Keep |
| Severe-dark brightness cutoff | Below 25 | Trial below 15 |
| Dim, weak-contrast cutoff | Brightness below 45 AND contrast below 20 | Trial brightness below 35 AND contrast below 20 |
| Assessment pause on poor sample | Immediate | Keep |
| Poor-light confirmation | Immediate | 2 continuous seconds |
| First lighting speech | Normally 3 seconds after first poor sample | At least 5 seconds: 2s confirmation + 3s warning delay |
| Recovery after confirmed poor light | 2 seconds of good samples | Keep |
| Recovery after unconfirmed dip | Full 2s recovery | Next fresh good sample if light was already usable before the dip |
| Existing face warning | 3s delay, 2s recovery | Keep unchanged |
| Visibility speech reminders | 30 seconds | Keep |

Brightness and contrast use a 0–255 scale. The 15/35 cutoffs are provisional
engineering values, not measured taxi limits. Examples:

| Face brightness / contrast | Current | Trial |
| --- | --- | --- |
| 18 / 30 | Poor | Usable candidate |
| 37 / 15 | Poor | Usable candidate |
| 30 / 15 | Poor | Poor |
| 12 / 50 | Poor | Poor |
| 30 / 25 | Usable candidate | Usable candidate |

User requested another five-point reduction from the earlier proposed brightness
cutoffs (20 to 15 and 40 to 35). Contrast remains 20; warning timings are unchanged.

Usable candidates still need fresh face tracking, alignment and normal initial
admission. Real dim-camera testing must show usable measurements before accepting
the new cutoffs. Revise or reject them if they admit unreliable dark frames.

## 3. Startup and calibration

1. Keep the current fast sampling and 1s freshness requirement throughout the app.
   Five- or ten-second polling would expire readings and leave decisions outdated.
2. Require 2s of continuous good facial samples for initial admission. A new
   target, connection or alignment cannot inherit old usable-light evidence.
3. During calibration, allow absent lighting samples for up to 2s since the last
   admitted usable lighting sample, only while fresh (<=1s) measurements from the
   locked, aligned face continue. Measured poor light, recovery, face loss, target/
   alignment changes and camera interruptions cancel this grace immediately.
   Initial admission and starting each phase still require fresh usable lighting.
   Missing facial measurements never advance the timer or enter baselines;
   actual poor light and longer gaps pause collection. Preserve finished steps.
4. Use the same lighting thresholds during calibration and monitoring so identical
   conditions are not accepted during setup and rejected after Start.
5. Keep visibility speech limited to active monitoring, as currently implemented.
   Do not add visibility alarm sounds during calibration.

## 4. Lighting timeline during monitoring

### A. Previously usable selected face becomes briefly dark

- First fresh poor facial sample: pause assessment immediately, show checking
  lighting and start the poor-light confirmation clock.
- Before 2s: no confirmed insufficient-lighting issue and no lighting speech.
- Good sample before 2s: discard the unconfirmed dip. Restore lighting eligibility
  on that new sample if lighting was already usable before the uninterrupted dip.
  All other face/alignment/channel gates remain required.
- If there was no prior usable state, good samples still need normal 2s admission.

### B. Poor selected-face lighting persists

- At 2s of continuous fresh poor evidence: confirm insufficient lighting and
  begin its own 3s warning delay.
- At 5s total, or the first qualifying result afterward: request the existing
  lighting speech if monitoring is active and lighting is still poor. Inference,
  transport and protected existing playback may delay actual audio further.
- A fresh good sample cancels a pending lighting announcement immediately.
  Assessment still waits for 2s of good recovery after confirmed poor lighting.
  Do not announce darkness while good light is recovering.
- Poor light returning during recovery cancels that recovery. Separated poor
  periods do not add together toward the next five-second warning qualification.
- Keep 30s reminders, visibility-episode continuity and Stage3 playback protection.

### C. Missing or interrupted evidence

- Missing/stale samples pause assessment without proving darkness. They break
  continuous dark confirmation, pending speech qualification and good recovery.
- Duplicate or out-of-order samples do not advance clocks. Transitions require
  a new sample with capture gaps no greater than the 1s freshness interval.
- Preserve the existing brief-gap quick resume only on a NEW good sample after
  a recently usable state. Neither confirmed darkness nor an interrupted pending
  dark dip may use that shortcut.
- Target, connection and alignment resets discard previous lighting evidence.
  Stop/reconnect must cancel or invalidate pending lighting announcements.

## 5. Existing face-not-visible behavior

- Remove only the lighting override: missing selected driver plus dim ambient
  scene must no longer become INSUFFICIENT_LIGHTING.
- Let the existing FACE_UNAVAILABLE path handle absent/ambiguous/out-of-area
  faces, with its existing wording, speaker track, 3s delay, recovery and reminders.
- The controller currently shares candidate elapsed time between face loss and
  lighting. Isolate lighting qualification so missing-face time cannot cause an
  immediate lighting announcement when a poor-lit face returns.
- Preserve episode continuity: a face alert already spoken must not produce an
  extra lighting announcement inside the existing 30s speech interval. Qualified
  attribution updates within that interval stay silent.
- A missing-face nod shorter than the existing 3s delay can remain silent. A
  sustained face-hidden nod can still trigger that existing face warning. This
  plan cannot measure head nodding reliably when only hair is visible.
- A covered lens with no face also uses the existing face-not-visible warning.
  Insufficient-lighting attribution needs fresh poor samples of a selected face.

## 6. Code inspection and implementation map

| File / area | Planned work |
| --- | --- |
| monitoring/FaceLightingTracker.kt | Trial cutoffs, poor confirmation, checking/recovery and conditional quick return; remove ambient-based face-loss attribution |
| monitoring/VisibilityAlertController.kt | Separate lighting qualification from face-loss time; preserve face timing and episode/reminder cadence |
| detection/DriverVisionViewModel.kt | Current qualified issue, consistent gates and cancellation/recheck of pending speech; retain sampling |
| monitoring/CameraVisibilityPresentation.kt | Remove duplicate ambient-dark/no-face override; agree on checking, poor and recovery |
| monitoring/DriveAlertMonitoringService.kt | Verify status priority cannot expose stale light attribution over current face loss/checking |
| ui/postauth/EntryAndHomeScreens.kt and DeviceSetupDetailScreens.kt | Verify home/setup/calibration status uses the same decision; edit only if needed |
| notification/DriverVisibilityNotifier.kt | Verify correct existing issue selects existing text; no new warning recording/type |
| Unit tests and app/DRIVEALERT_DETECTION.md | Regressions and final settings/behavior documentation |

Use one shared lighting decision across presentation paths. This map identifies
inspection points; not every listed file necessarily needs a change.

## 7. Automated acceptance cases

| Scenario | Required result |
| --- | --- |
| Dim scene, usable selected face | Assessment eligible; no lighting warning |
| Face missing in dim or bright scene | Existing face issue, never inferred light failure |
| Face alert timing | Existing 3s delay, 2s recovery and 30s reminder tests still pass |
| Previously usable face, 1.8s poor crop, then good | Pause during poor interval; no light speech; new good sample may restore eligibility |
| No prior good state, one good sample | Still needs initial 2s admission |
| Continuous poor selected face | No light speech before 5s; one notification qualifies at/after 5s |
| Good sample just before 5s | Pending speech cancelled; confirmed-dark recovery still needs 2s |
| Poor/good/poor intervals | Separated darkness never adds together |
| Missing/stale/duplicate/future/out-of-order evidence | Cannot establish continuous darkness or recovery |
| Face loss then poor selected face | Missing-face time cannot shorten lighting qualification |
| Reported visibility episode changes attribution | No duplicate speech inside 30s; only qualified UI updates |
| Stop, reconnect or target/alignment reset | No old pending announcement or inherited lighting evidence |
| Cutoff boundaries 14.9/15, 34.9/35, contrast 19.9/20 | Strict comparisons match the trial rule with no dead band |
| Calibration lighting gap with fresh face | Continue real measurements for at most2s since last usable sample; no initial grace; poor light/face loss/reset cancels |
| Calibration interrupted by poor light | No bad samples in baselines or collection time; phase recovery preserved |
| Existing regional and drowsiness behavior | Unrelated tests remain passing |

Run Android unit tests, assembleDebug and compileDebugAndroidTestSources on JDK17.
Use focused presentation/runtime tests where practical. These checks establish
software behavior, not real-camera accuracy.

## 8. Parked camera check and delivery

1. Build a separate candidate APK after implementation and software checks.
2. Check face brightness/contrast and measurement availability while facing ahead
   under taxi-like cabin light. Confirm the trial cutoffs consistently admit a
   visibly usable face. Prefer bounded numeric diagnostics to image collection.
3. Try a short nod and return: no ambient-based light warning; existing face
   behavior unchanged. Verify measurement availability after reacquisition.
4. Reduce actual selected-face lighting continuously: verify immediate assessment
   pause, no early light speech, eventual correct warning, and good-light recovery.
5. Check sustained face loss/covered lens separately: original face alert still
   works. Check restored view and repeated poor/good transitions.
6. Check calibration under usable dim light and interruptions. Reject unusable
   frames from all baselines. Do not claim this fixes downward-gaze/eye-closure
   ambiguity; that earlier investigation remains stopped.
7. Report software and physical-camera evidence separately. Revise/reject trial
   thresholds if facial measurements become unreliable in admitted darkness.

No ESP32 firmware, cloud change or new recording is planned. The APK is installed
with existing data preserved; no additional commit/push has been performed.

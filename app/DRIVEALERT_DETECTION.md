# DriveAlert Android detection core

## Facial lighting and missing-face attribution (2026-11-16)

Facial pixels are poor when trimmed luminance is below15, or luminance is below35
and contrast is below20 (0–255). These are relaxed engineering settings awaiting
parked-camera validation in dim cabin light. Sampling remains at most every200ms
as frames permit; accepted lighting samples must still be no older than1s.

A poor sample pauses assessment immediately but remains checking/UNKNOWN until
two continuous seconds of new poor samples confirm INSUFFICIENT. The visibility
controller then requires three further seconds of qualifying sample timestamps
before requesting lighting speech (at least five seconds total). It cannot use
missing-face time or advance from repeated cached samples. Missing/stale evidence
breaks qualification. New poor intervals cannot reuse a previous interval's delay.

A fresh good sample cancels pending lighting speech. Previously usable lighting
can return immediately after an uninterrupted, unconfirmed dip; confirmed darkness
and initial admission require two seconds of continuous good samples. A pending
dark interval or missing evidence is not recovery from an already reported episode.
The existing thirty-second speech cadence remains shared across attribution changes.

Ambient scene darkness is guidance only. Missing, ambiguous or out-of-area drivers
use the existing face-unavailable path with its original three-second warning,
two-second recovery, text and speaker track. UI/setup/service status must agree;
old reported lighting must not override current face loss or lighting recovery.
Before sending delayed lighting speech or posting its notification, the runtime
rechecks fresh confirmed poor lighting, the same poor-interval timestamp, driver
lock/alignment, stream and session. Good light, a new interval or interruption
invalidates the request. No new warning type, recording or firmware is involved.

## Calibration lighting gaps (2026-11-16)

Startup and each phase's admission still require fresh usable lighting. During
collection, CalibrationLightingGrace permits up to two seconds since the last
admitted usable lighting sample when lighting is absent but fresh (<=1s) locked,
aligned face measurements continue. The five-second timer uses only actual valid
facial measurements. No absent-face samples are invented. Measured poor light,
recovery, target/alignment/connection changes and camera stalls cancel grace;
longer gaps pause collection. Monitoring retains its strict lighting freshness.
The calibration screen stays In progress during eligible grace and shows checking
lighting. Sampling frequency and visibility warning timing are unchanged.

## Visibility episode continuity

Once a sustained visibility problem has been reported, a change between mouth,
eye, combined obstruction, face loss or lighting attribution does not dismiss
the acknowledgement dialog or restart the 30-second speech cadence. A new
attribution must still meet its confirmation delay before updating dialog
content; updates inside the existing cadence are silent. The dialog clears on
two seconds of observed recovery, explicit user action, session stop or camera
interruption. Acknowledging a regional obstruction never bypasses fresh face,
lighting, driver-lock or channel-availability checks. This stabilizes warning
presentation; it does not improve the underlying classifier's accuracy.

## Responsibility and video path

The operational camera is the OV3660 IR-capable sensor attached to an AI-Thinker-compatible classic ESP32-CAM/ESP-32S board with approximately 4 MB PSRAM. Android is the local processing device, not the capture camera. The ESP32-CAM-MB is currently used for USB programming and power. The intended offline path is:

`OV3660 -> classic ESP32-CAM -> local Wi-Fi -> Android receiver/decoder -> MediaPipe -> DriveAlertDetectionEngine`

The operational endpoint is `http://<ESP32-IP>:81/stream`, with `_drivealert._tcp` mDNS discovery after the persisted IP becomes stale. `Esp32MjpegFrameSource` parses the bounded multipart stream, decodes JPEG frames once, assigns monotonic timestamps, and shares each bitmap between Compose preview and `MPImage`/MediaPipe with explicit ownership. The graph-scoped `DriverVisionViewModel` owns stream recovery, MediaPipe, the detection engine, alignment confirmation, and calibration state.

No CameraX dependency, phone-camera source, `PreviewView`, `ImageAnalysis`, or Android camera permission is part of this integration.

## MediaPipe configuration

- Dependency: `com.google.mediapipe:tasks-vision:1.0.0`
- Running mode: `VIDEO`
- Faces: up to four in the scene graph, with an independent one-face VIDEO
  tracker kept warm on the same full frames. The one-face result is considered
  only when the scene graph returns zero faces, then passes the same driver-area,
  continuity, acquisition and freshness gates. It never overrides scene
  competitors or capacity saturation. This recovery targets partial-obstruction
  dropouts; it is not proof of reliable mouth recognition at every distance.
- Facial landmarks: enabled
- Facial blendshapes: enabled only in debug builds for numeric investigation.
  `DriveAlertEyeExpression` logs blink, look-down and squint activations for the
  selected current face, paired by source timestamp. They do not affect
  calibration, channel availability or alarms. No images are saved. These
  landmark-derived activations are not independent evidence or calibrated
  probabilities; real gaze/closure separation remains unverified.
  The same debug line includes raw EAR and raw pitch before assessment gates.
  `DriveAlertEyes` separately logs alignment, lighting eligibility, face lock,
  monitoring status and source timestamp. Raw geometry is diagnostic evidence,
  never authorization to bypass a failed assessment gate.
- Facial transformation matrices: enabled
- Model asset: `app/src/main/assets/face_landmarker.task`
- Model provenance: byte-for-byte copy of `drivealert-ai-prototype/models/face_landmarker.task`
- SHA-256: `64184E229B263107BC2B804C6625DB1341FF2BB731874B0BCC2FE6544E0BC9FF`

Inference runs on a dedicated single-thread executor. If an inference is already active, a newly submitted stale frame is released and dropped. Closing the processor queues MediaPipe resource closure on the same executor.

The recovery graph retains the original image coordinates and pose geometry; it
does not feed manually zoomed image coordinates into saved calibration. Both
graphs infer from the current bitmap, and neither substitutes old landmarks for
an undetected driver. MediaPipe's graph invokes detection again when fewer than
the configured maximum faces are tracked; a one-face VIDEO graph can instead
continue its internal face-crop tracking. See the
[upstream graph](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/tasks/cc/vision/face_landmarker/face_landmarker_graph.cc).

MediaPipe and FaceAttribNet initialize lazily on that executor when the first
camera frame is processed. App launch, sign-in, alignment configuration and closing
an unused processor do not load either model. Initialization therefore does not
block the main UI thread; the first camera assessment waits for model readiness.

## Driver movement continuity

Driver selection allows overlapping head movement inside the configured driver
area: successive boxes must share at least 35% of the smaller box, center shift
must be at most 80% of the previous box's larger dimension, and the area ratio
must remain between 0.4 and 2.5. These are provisional spatial association
defaults. Missing faces, competing faces, out-of-area detections, non-overlapping
jumps and stale frames still break tracking and temporal evidence. This is not
identity verification. Automated movement tests do not validate real-camera
noise tolerance or every head turn.

## FaceAttribNet lower-face visibility

The Android processor also packages Qualcomm's universal W8A8 FaceAttribNet
TensorFlow Lite export. It runs diagnostically at most once every 500 ms on a
128 x 128 padded face crop from the same bitmap already accepted for MediaPipe.
The ESP32 performs no additional inference and the MJPEG frame is not decoded a
second time.

The five independent outputs are left-eye openness, right-eye openness,
eyeglasses, mask, and sunglasses. Validation on the operational ESP32-CAM feed
showed mask probabilities of 0.996 for a shirt or hand over the lower face,
0.000-0.020 for a clear face, and at most 0.400 for an uncovered open mouth.
DriveAlert therefore interprets this output as lower-face obstruction: two
samples at or above 0.80 disable MAR/yawning, and three samples below 0.50
restore it. Sunglasses were also validated on the operational feed and use the
same 0.80 activation and 0.50 recovery thresholds to disable EAR/eye-closure
monitoring. When both regions are obstructed, head-pose monitoring is disabled
as well and monitoring is paused. Cached results expire after two seconds.
Eyeglasses remain diagnostic because clear eyeglasses have not been tested.

Eye-openness outputs provide a disagreement check: either eye must score
at least 0.90 on available, unobstructed attribute evidence captured no more
than 750 ms before the current observation. A confidently open eye contradicts
bilateral closure and rearms the timer even if mask-related landmark distortion
or downward gaze compresses EAR. This does not establish that both eyes are open.
Unknown, stale, invalid or only weakly open evidence cannot veto closure. Actual closure still
requires the existing two seconds below the calibrated EAR threshold; the EAR
formula, calibration and yawn/head rules are unchanged. This cross-check needs
live verification for genuine closure and glasses; model outputs can be wrong.

A lower-face obstruction sustained for two seconds produces a mobile visibility
notification and a dedicated spoken request to the onboard speaker without
creating a drowsiness event or changing warning-stage history. Eye obstruction
uses "Eyes blocked", lower-face obstruction uses "Mouth blocked", and combined
obstruction or face loss uses "Face not visible". The spoken request repeats
every 30 seconds while the same obstruction remains active.
Complete face loss uses a one-second notification delay. The visibility
notification clears after one continuous second of recovery. Sunglasses alone
use the same two-second delay as lower-face obstruction; simultaneous eye and
lower-face obstruction uses the one-second pause delay.

Model provenance and tensor metadata are recorded in
`app/src/main/assets/FACE_ATTRIB_NET.md`.

## Paper-locked operational rules

- EAR uses landmarks `(362, 385, 387, 263, 373, 380)` and `(33, 160, 158, 133, 153, 144)`. Normalized coordinates are scaled to image pixels before Euclidean distances are calculated. The two eye ratios are averaged.
- `EARThreshold = mean(valid NEUTRAL EAR) * 0.75` after five valid seconds.
- MAR uses corners `78, 308` and vertical pairs `81/178`, `13/14`, and `311/402`, with the three vertical distances divided by twice the corner distance.
- `MARThreshold = mean(valid MOUTH_OPEN MAR) * 0.50` after five valid seconds. Neutral MAR is diagnostic only.
- Raw head pitch mirrors Python's `atan2(rotation[2,1], rotation[2,2])`. MediaPipe Java exposes a flat column-major matrix, so Android reads flat indices `6` and `10`.
- Neutral-relative pitch is wrapped to `[-180, 180)`. The guided `HEAD_DOWN` phase establishes the multiplier that makes downward motion positive.
- Eye closure confirms when `EAR < threshold` continuously for at least 2,000 ms.
- Yawning confirms when `MAR > threshold` continuously for at least 3,000 ms.
- A valid head observation is downward only when relative pitch is strictly greater than `15.0°`. Head nodding confirms when at least 80% of valid observations in the rolling 3,000 ms window are downward.
- Timing uses monotonic frame timestamps, never frame counts or wall-clock time.
- A continuous qualifying episode emits one event. A valid recovery observation re-arms its detector.
- While a confirmed sign remains observable, Android requests a current-stage
  warning reminder every 2,000 ms for eye closure and every 3,000 ms for yawning
  or head down, initially measured from confirmation. Successful camera acceptance
  defers the next reminder by at least 2,300 ms for ordinary alarms, 10,300 ms
  for Stage 3 mixed tracks, or 4,300 ms after visibility speech. These include a
  margin over the existing firmware's estimated playback guards; they are not
  measured sound-file lengths. Late observations schedule from the actual request
  instead of catching up to an earlier slot. Reminders do not create detection
  events, history entries, phone notifications, or independently advance stages.
  Existing 60-second persistence escalation remains active. Simultaneous due
  reminders share one request; stage-change warnings supersede reminders.
- Recovery or channel unavailability clears that sign's reminder schedule.
  Missing/stale tracking, lighting pauses, and session termination stop reminders;
  delayed observations never produce a backlog. Delivery runs separately from
  frame processing, with at most one reminder request in flight.
- An accepted Stage 3 mixed track covers new warning events for its 10,300 ms
  reservation instead of being restarted mid-sentence. Event/history accounting
  continues, but no fictitious HTTP request is logged for a coalesced warning.
  Visibility speech waits for that reservation and rechecks its issue/session.
- Capture age still gates individual results at 1,000 ms. Pipeline-stall detection
  measures silence since the last valid result completed, so normal inference
  latency is not counted twice. Stale UI readings are hidden immediately;
  temporal evidence is reset on true stalls, bad observations or unusable light.
- Missing measurements never become positive detections. Eye/yawn candidates reset on invalid input; invalid head observations are excluded. Face loss clears all temporal evidence.

## Calibration and event boundary

Each calibration step waits in READY until the user acknowledges its instructions
with **Okay**. The UI gives a three-second preparation countdown, then requests
collection with the expected phase and navigation revision. A stale acknowledgment
cannot start a step after a reset or a different step. Lighting/tracking pauses
still require fresh usable observations before collection can start.

**Go Back to Previous Step** clears that previous step and all later measurements,
retaining earlier completed steps. It is disabled on the first step. **Redo All
Steps**, directly below it, requires confirmation and resets the attempt to normal
driving posture. Previously persisted calibration is replaced only on successful
completion, as before. The calibration revision is in-memory UI coordination;
there is no database migration or durable acknowledgment log.

Instructions explicitly use the driver's normal posture looking ahead toward the
road, while parked, rather than asking the driver to turn toward the camera. Eye,
mouth and head-down instructions are shown before and during their respective steps.

Transient measurement/lighting freshness timeouts pause evidence without
reconfiguring the face selector or declaring a new driver. They break calibration
timing continuity and drowsiness candidates, but preserve valid calibration time
and completed phases. True target changes, realignment and disconnects still
invalidate unfinished calibration as before.

Missing/stale lighting samples never enable assessment. A new explicitly usable
sample may restore a previously usable lighting decision within two seconds of
the previous sample, avoiding repeated recovery loops during brief processing
delays. Initial lighting, confirmed poor pixels, borderline pixels after a gap and
longer interruptions still require normal two-second recovery. An uninterrupted
unconfirmed dark dip can end on a new good sample if light was previously usable.
The one-second sample freshness bound remains unchanged; current pixel thresholds
and poor-light confirmation are documented above.

Calibration follows `NEUTRAL -> EYES_CLOSED -> MOUTH_OPEN -> HEAD_DOWN`. Each phase accumulates five seconds of valid contiguous intervals. Invalid observations break timing continuity and are not stored as zero. Completed results are persisted in Room per Firebase Driver ID; history is retained and one record is active per Driver for offline monitoring.

`ConfirmedSignEvent` is the output boundary for later warning-stage work. It includes sign type, monotonic confirmation timestamp, qualifying duration, measurement, threshold, calibration ID/version, and optional temporal context.

## Driver movement advisory

`DriverMovementTracker` runs only in active monitoring with fresh selected-driver
observations, confirmed alignment and usable lighting. It reuses numerical landmarks
at most five times per second; no additional inference or image history is needed.
The left/right eye and mouth groups match the existing measurement landmarks.
Median landmark displacement within any group must reach 2% of reference face
width in two consecutive samples at least 200 ms apart. Coordinates are in pixels;
whole-face position, rotation and size changes deliberately count as movement.
The reference is the coordinate-wise median of three samples and is retained until
qualifying movement, allowing gradual changes to accumulate.

Both inactivity timers start at the first eligible observation, then reset on each
qualifying movement. At 10 seconds the monitoring screen shows **No movement
detected**; at 25 seconds total Android requests `issue=MOVEMENT` once per episode.
Drowsiness signs, existing visibility guidance and in-flight speaker requests have
priority. A suppressed advisory may be sent after those conditions clear. A failed
request is recorded in memory and is not automatically retried.

Tracking loss, invalid or stale input, poor lighting, target changes, realignment,
disconnects and session termination clear movement evidence and timers. Restored
observations start fresh. This tracker never pauses drowsiness assessment, changes
calibration, creates drowsiness history or changes warning-stage counts.

These are configurable **provisional engineering defaults**, not validated liveness
thresholds. Moving photographs and replayed videos can pass. Stationary closed-eye
photographs can still trigger drowsiness alerts by design; the movement advisory is
separate and does not authenticate the input. Camera-noise tolerance and physical
speaker playback remain hardware acceptance tasks.

See `drivealert-iot/deployment/README.md` for the firmware update and track 15.

## Integration boundaries

- Lower-face obstruction disables yawning, sunglasses disable eye monitoring,
  and simultaneous obstruction pauses all facial-sign channels.
- Warning Stages 1/2/3, Room Alert History, Stage 3 synchronization, and the Android-to-ESP32 warning-command transport are downstream consumers of `ConfirmedSignEvent`; they do not alter detection behavior.
- Physical Android-to-speaker playback requires manual hardware verification even when the software builds and transport tests pass.
- No Hand Landmarker, YOLO, dataset collection, or training code is included.
- FaceAttribNet is validated for lower-face obstruction and sunglasses on the
  current driver/device setup. It does not establish arbitrary eye-region
  obstruction from hands, hair, or other unknown objects.

## Verification

Run from the repository root in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

The JVM tests use deterministic numeric landmarks, matrices, and measurement sequences. They do not require a camera or a live ESP32 stream.

## Manual validation

1. Provision the exact Wi-Fi name and confirm Android receives IP plus hostname.
2. Open Camera Alignment, verify live frames and face visibility, then confirm alignment manually.
3. Complete each guided calibration phase and restart the app to verify offline restoration.
4. Exercise eye closure, yawn, head-down, face loss, stream restart, and DHCP-address-change recovery.
5. With updated firmware, sustain each sign: eye closure should request warnings
   at roughly 2/4/6 seconds; yawning and qualifying head-down at roughly 3/6/9
   seconds. Confirm a single history incident, no repeat-driven escalation,
   recovery stopping reminders, and the existing 60-second stage transition.
   Check all sound tracks, especially long mixed Stage 3 recordings: firmware
   suppresses reminders during estimated playback reservations, so actual audible
   repetitions can be slower than requests. See the warning-output protocol.

Live ESP32-to-Android operation remains physically unvalidated until these device tests complete.

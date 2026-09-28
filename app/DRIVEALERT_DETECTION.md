# DriveAlert Android detection core

## Responsibility and video path

The operational camera is the OV3660 IR-capable sensor attached to an AI-Thinker-compatible classic ESP32-CAM/ESP-32S board with approximately 4 MB PSRAM. Android is the local processing device, not the capture camera. The ESP32-CAM-MB is currently used for USB programming and power. The intended offline path is:

`OV3660 -> classic ESP32-CAM -> local Wi-Fi -> Android receiver/decoder -> MediaPipe -> DriveAlertDetectionEngine`

The operational endpoint is `http://<ESP32-IP>:81/stream`, with `_drivealert._tcp` mDNS discovery after the persisted IP becomes stale. `Esp32MjpegFrameSource` parses the bounded multipart stream, decodes JPEG frames once, assigns monotonic timestamps, and shares each bitmap between Compose preview and `MPImage`/MediaPipe with explicit ownership. The graph-scoped `DriverVisionViewModel` owns stream recovery, MediaPipe, the detection engine, alignment confirmation, and calibration state.

No CameraX dependency, phone-camera source, `PreviewView`, `ImageAnalysis`, or Android camera permission is part of this integration.

## MediaPipe configuration

- Dependency: `com.google.mediapipe:tasks-vision:1.0.0`
- Running mode: `VIDEO`
- Faces: one
- Facial landmarks: enabled
- Facial transformation matrices: enabled
- Model asset: `app/src/main/assets/face_landmarker.task`
- Model provenance: byte-for-byte copy of `drivealert-ai-prototype/models/face_landmarker.task`
- SHA-256: `64184E229B263107BC2B804C6625DB1341FF2BB731874B0BCC2FE6544E0BC9FF`

Inference runs on a dedicated single-thread executor. If an inference is already active, a newly submitted stale frame is released and dropped. Closing the processor queues MediaPipe resource closure on the same executor.

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
- Missing measurements never become positive detections. Eye/yawn candidates reset on invalid input; invalid head observations are excluded. Face loss clears all temporal evidence.

## Calibration and event boundary

Calibration follows `NEUTRAL -> EYES_CLOSED -> MOUTH_OPEN -> HEAD_DOWN`. Each phase accumulates five seconds of valid contiguous intervals. Invalid observations break timing continuity and are not stored as zero. Completed results are persisted in Room per Firebase Driver ID; history is retained and one record is active per Driver for offline monitoring.

`ConfirmedSignEvent` is the output boundary for later warning-stage work. It includes sign type, monotonic confirmation timestamp, qualifying duration, measurement, threshold, calibration ID/version, and optional temporal context.

## Integration boundaries

- Regional obstruction/reliability logic remains experimental and non-operational.
- Warning Stages 1/2/3, Room Alert History, Stage 3 synchronization, and the Android-to-ESP32 warning-command transport are downstream consumers of `ConfirmedSignEvent`; they do not alter detection behavior.
- Physical Android-to-speaker playback requires manual hardware verification even when the software builds and transport tests pass.
- No Hand Landmarker, YOLO, CNN, classifier, dataset collection, or training code is included.

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

Live ESP32-to-Android operation remains physically unvalidated until these device tests complete.

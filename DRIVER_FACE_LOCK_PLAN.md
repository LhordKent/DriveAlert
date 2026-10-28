# Driver face lock implementation plan

Status: planned; no feature implementation yet.
Branch: `codex/driver-face-lock`, based on `main` at `00747cd`.

## Objective and boundaries

Assess the person in the configured driver's seating area while ignoring other visible faces. Use spatial continuity, not identity verification, face recognition, biometric enrollment, or stored face images. A different person occupying the driver's position cannot be distinguished reliably by this feature.

Assessment must stop whenever the target is missing or ambiguous. Pausing is an unavailable assessment, never evidence that the driver is alert. Previously confirmed alert history must survive a pause.

## Current integration points

- `app/src/main/java/com/lhordkent/drivealert/detection/face/MediaPipeFaceLandmarkerProcessor.kt`: requests one face and selects the first landmarks and transformation matrix. It also owns cached face attributes and lighting samples.
- `detection/DriverVisionViewModel.kt` (under the same package): delivers one observation to the engine, gates assessment on lighting, manages stream generations, calibration, visibility alerts, and monitoring effects.
- `detection/DriveAlertDetectionEngine.kt`: already clears temporal evidence and interrupts calibration continuity when observations are missing or assessment is disallowed. `invalidateTracking()` preserves saved calibration and alert history.
- `ui/postauth/DeviceSetupDetailScreens.kt`: camera alignment and calibration screens, including the live preview.
- `monitoring/CameraVisibilityPresentation.kt`, `monitoring/DriveAlertMonitoringService.kt`, and the home/monitoring screens: user-facing availability status must agree with the assessment gate.

## Implementation sequence

### 1. Define the target-selection contract

Add pure Kotlin models for a normalized driver region, face bounds, target-selection status, selected observation, and a transient tracking generation. Proposed states: `UNCONFIGURED`, `ACQUIRING`, `LOCKED`, `LOST`, and `AMBIGUOUS`.

Only `LOCKED` permits assessment, and existing lighting and visibility requirements still apply. Keep “some face detected” separate from “driver selected.” Face-list order must never serve as target identity.

### 2. Configure the driver's region during alignment

Add a region overlay to the existing camera alignment preview. Allow adjustment while stationary, with a centered default for a driver-facing installation. Persist normalized bounds locally with the camera/device configuration; do not tie the region to a person's face.

Map the overlay correctly through preview scaling, cropping, rotation, and mirroring into inference coordinates. Require exactly one stable eligible face before confirming alignment. Include space for normal head turns and the head-down calibration phase. Existing installations require region confirmation before assessment is enabled.

Invalidate setup when the camera/device or coordinate configuration changes. Explain that moving the physical mount requires realignment; this feature cannot reliably detect every mount movement.

### 3. Detect candidates and select the driver before analysis

Update the processor to return multiple face candidates with landmarks and transformation matrices paired by result index. Start evaluation with a configurable capacity of four faces. Verify MediaPipe multi-face behavior, smoothing, and target-device throughput before fixing the production setting. At capacity, conservatively pause because additional competing faces may be omitted.

Implement a pure `DriverFaceSelector` using region eligibility, bounding-box overlap, center displacement, size continuity, and elapsed time. Reject implausible jumps and similar competing matches. Do not choose the largest face or first result as a fallback.

Acquire only when one eligible candidate remains stable for a proposed one second. Once locked, ignore clear non-competing faces outside the driver region. Pause immediately when the selected face is missing, moves outside the allowed region, or cannot be associated unambiguously. An overlapping competitor can cause ambiguity even when its center is outside the region.

After loss, require a fresh stable acquisition; never transfer assessment directly to another candidate. Tune geometric tolerances and timing against recordings rather than treating initial constants as validated guarantees.

### 4. Gate the full pipeline and reset transient evidence

Run face attributes and face-lighting sampling only for the selected driver. On target loss, ambiguity, reacquisition, region changes, or stream resets, clear cached attributes, lighting samples, and selector evidence so one person's values cannot carry into another observation.

In `DriverVisionViewModel`, combine target lock with lighting eligibility before calling the engine. Reuse the engine's unavailable path and `invalidateTracking()` to clear eye-closure, yawn, and head-down evidence and interrupt calibration timing. Ensure incomplete calibration samples cannot span target generations; restart the affected calibration collection as needed after inspecting `CalibrationSession` behavior.

Propagate unavailable status into the monitoring controller so no stale confirmed signs generate new drowsiness escalation. Preserve already confirmed events and existing alert acknowledgement rules. Reject callbacks from obsolete stream/target generations, and clear the lock on stale frames, disconnects, and device changes.

### 5. Present availability consistently

Show the configured region and selected-face outline on setup and monitoring previews. Use brief states such as “Finding driver,” “Monitoring driver,” and “Driver not clearly visible.” Explain ambiguity with “Keep the driver's face clear.” Additional non-competing faces should not produce warnings.

Keep calibration, home status, monitoring preview, foreground-service notification, and published monitoring status consistent: an unlocked target must not appear as active assessment. Reuse visibility-alert timing and cooldown conventions without recording ambiguous faces as drowsiness events.

### 6. Validate before enabling

Automated coverage:

- One stable driver acquires; a passenger entering outside the region does not change the selected target.
- Reordered detection results and a larger passenger face do not switch the target.
- Overlap, competing in-region faces, implausible jumps, no face, and detector-capacity saturation pause assessment.
- Driver loss followed by a passenger remaining outside the region never reacquires that passenger.
- Reacquisition waits for stability and cannot combine pre-loss and post-loss temporal evidence.
- Normal head movements and all calibration poses stay usable inside the configured region.
- Low light, stale frames, reconnects, and region/device changes clear the appropriate caches and invalidate outdated callbacks.
- Calibration cannot complete from mixed target generations; paused states do not produce new drowsiness events or erase confirmed history.
- Overlay-to-inference mapping remains correct with rotation, mirroring, and different preview aspect ratios.

Run focused selector, engine, calibration, and monitoring-controller tests, then the Android unit suite and debug build. Perform device checks using the actual camera stream with a driver and passenger entering, leaving, leaning across, and overlapping. Measure frame latency, dropped frames, and measurement stability with one, two, and several faces; verify eye and head thresholds remain usable in multi-face mode.

## Acceptance criteria

1. A clear passenger outside the driver area cannot take over an established driver track in the tested scenarios.
2. Missing or ambiguous target selection stops new drowsiness evidence immediately and visibly pauses assessment.
3. Resuming requires stable acquisition and fresh measurements, without stale attributes, lighting, or calibration samples.
4. No identity verification or biometric persistence is introduced.
5. Device validation establishes acceptable performance and records remaining limitations before release.

## Known limitation

Spatial tracking reduces unintended switches but cannot prove identity. A sufficiently similar replacement in the driver's position may be accepted, and detection errors may still defeat geometric association. Camera framing, region setup, conservative ambiguity handling, and real-device validation remain essential.

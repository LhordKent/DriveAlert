# Driver face lock implementation plan

Status: implemented on the feature branch; automated validation passed. Real-camera validation remains a release requirement.
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


## Implementation and validation record - 2026-10-29

- Added a pure spatial selector with one-second acquisition, a one-second continuity limit, conservative ambiguity handling, and a configurable detector capacity (default four). At capacity, assessment pauses.
- Added locally saved region geometry scoped to account, camera ID, stream port/path, and decoded frame dimensions. Existing installations must confirm a region. IP address changes do not invalidate a fixed mount; frame-dimension changes do.
- Selection precedes attribute classification and lighting samples. Configuration revisions reject old processor results; track generations reset caches and incomplete evidence.
- Target loss restarts **all unfinished calibration phases**, including neutral samples. The user explicitly starts the neutral phase again after reacquisition. Completed calibration and confirmed warning history are preserved.
- The preview uses the same decoded bitmap and its native aspect ratio as inference, without cropping, rotation, or mirroring. This avoids preview-to-inference coordinate conversion. A future display transform must also transform the overlay.
- Added region controls and outlines to alignment, driver tracking guidance to calibration and monitoring, and paused state integration in home/service presentation. Adjustment is disabled during calibration and monitoring.
- Validation: Android debug build and 215 unit tests passed; four dedicated UI/persistence/native-processor instrumentation tests passed on a Pixel 7 API 35 emulator, including 1.3 font scale. The native smoke test loads the multi-face processor and verifies a blank frame is rejected. Screenshot fixtures use a blank synthetic frame and do not demonstrate real face inference.
- MediaPipe documentation confirms smoothing is applied only for one-face mode: https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker . Multi-face measurement stability, latency, and threshold suitability require actual camera testing. No unvalidated smoothing filter or threshold retuning was added.
- Remaining release checks: real driver/passenger entry, overlap, disappearance, head poses, lighting transitions, frame rates and capacity on the target phone/ESP32-CAM installation. A geometric match cannot establish identity.

UI review corrections: the alignment preview stays pinned above the scrolling controls, and the foreground notification asks users to confirm the driver area when alignment is invalidated. Existing design tokens and visual-system documents are unchanged.


## Device timing correction - 2026-10-29

TECNO LE6h logs showed clear one-face results taking about 60-70 ms for landmarks and 358-375 ms when the serial attribute pass ran. The 500 ms capture-age watchdog expired valid work at 501-564 ms, and occasional processed-frame gaps also exceeded 500 ms. Both paths repeatedly discarded the target and lighting recovery. The shared continuity/result-age/watchdog bound is now 1,000 ms, matching facial lighting freshness. Missing faces, competing faces, spatial jumps, and stale gaps still invalidate assessment. A regression replays alternating 600/150 ms capture gaps and checks that lighting can finish recovery without changing target generation.

Correction validation: 216 unit tests and the debug build passed; update installed in place on the TECNO LE6h. Post-fix live phone logs showed the same target continuously LOCKED from 18:51:27 through 18:51:42, with lighting becoming USABLE at 18:51:28.997. No timeout reset appeared in that observed interval; broader passenger/occlusion validation remains required.

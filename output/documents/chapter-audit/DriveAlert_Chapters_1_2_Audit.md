# DriveAlert Chapters 1 and 2 consistency audit

Audit date: November 2, 2026

**Verdict: Chapters 1 and 2 are substantially aligned with the implemented detection and warning-stage architecture, but are not yet fully consistent or ready for a final technical defense.** The numerical detection rules and most Stage 3 synchronization rules are correct. The strongest confirmed problems are contradictory infrared descriptions, missing bibliography entries for central design claims, incomplete warning-trigger descriptions, and a mismatch between the manuscript's session-highest-stage definition and the dashboard calculation.

This report contains 24 findings. They include confirmed contradictions, implementation discrepancies, omissions, and precision issues; they are not 24 equally serious defects. Each is classified below so that a missing feature description is not mistaken for a broken implementation.

The Word document and application source were not changed.

## Scope and evidence limits

The manuscript reviewed is `C:\Users\Admin\Downloads\DriveAlertlatest.docx`. Chapter I spans extracted top-level paragraphs P0073–P0198; Chapter II spans P0224–P0336, including its comparative tables. The Figure 1 text boxes and the reference list were also inspected. The rest of Chapter III was not audited, even where its tables appeared during extraction.

P identifiers in this report are zero-based positions in Word's top-level paragraph collection. They are reproducible extraction locators, **not printed page numbers**. Use the section name and quoted phrase to find passages in Word. Paragraph references are deliberately used because pagination could not be verified: the packaged DOCX renderer failed because `soffice.exe` is unavailable, and the selected runtime has no bundled LibreOffice. This is a content and system-consistency audit, not a completed visual-layout audit. Embedded raster images were not comprehensively inspected; the editable Figure 1 text was inspected.

The implementation authority is the current working tree in `C:\DriveAlert`: Android production source, active ESP32 firmware, web portal source and documentation, synchronization code, and relevant data repositories. This is not proof of which APK or firmware is currently installed on a physical device. No fresh hardware, road, cloud-deployment, or automated test run was performed for this audit. Existing repository validation statements were treated as historical evidence, not rerun results.

Selected primary research and official product pages were checked online. This does not constitute exhaustive verification of every statistic, reference, DOI, or competitor feature. An absent bibliography entry does not imply that the cited paper does not exist.

## What already matches the implementation

These are important strengths to preserve when revising the manuscript.

| Manuscript claim | Finding against current source |
|---|---|
| External ESP32 camera captures video; Android processes it locally | Consistent with the MJPEG source and MediaPipe processing path |
| Three drowsiness-sign channels | Consistent: prolonged eye closure, yawning, head-down detection |
| Four calibration phases | Consistent: neutral, eyes closed, mouth open, head down |
| Five seconds of valid measurements per phase | Consistent, subject to the timing clarification in F15 |
| EAR threshold equals 75% of neutral open-eye EAR | Exact match in `CalibrationSession.buildResult` |
| Closed-eye EAR is a reference rather than the operational threshold formula | Consistent |
| MAR threshold equals 50% of average open-mouth MAR | Exact match |
| Head calibration establishes neutral pitch and downward direction | Consistent |
| Head angular deviation remains fixed at 15 degrees | Consistent; it is not individually learned |
| Eye closure requires EAR below threshold for at least 2 seconds | Consistent |
| Yawning requires MAR above threshold for at least 3 seconds | Consistent |
| Head-down qualification uses greater than 15 degrees and at least 80% of valid observations over a rolling 3-second window | Consistent, with window/recovery details in F14 |
| Signs need not all occur simultaneously | Consistent |
| A continuous qualifying sign does not repeatedly emit new sign events | Consistent under uninterrupted valid tracking |
| Overlapping confirmed signs can belong to one alert | Consistent, but the actual merge rule is deterministic; see F12 |
| First distinct episode activates Stage 1 | Consistent |
| New distinct episodes immediately advance Stage 1 to 2 and Stage 2 to 3 | Consistent |
| Stage evaluation periods are 60 seconds | Exact match |
| Persistent confirmed activity can escalate at a boundary | Consistent |
| Stage 1 can return to normal; Stage 2 can fall to Stage 1 | Consistent |
| Stage 3 is the maximum; no Stage 4 | Consistent |
| Stage 3 recurrence does not restart its existing period | Consistent |
| Stage 3 recurrence or boundary persistence can maintain Stage 3 | Consistent |
| Stages 1 and 2 use the same selected sound; Stage 3 uses a mixed sound and rest advisory | Consistent with the physical firmware track mapping; see the code-only discrepancy later |
| Local alerts and Stage 3 synchronization have separate counting boundaries | Consistent with separate repositories and entities |
| One Stage 3 transition record is created on entry | Consistent; later re-entry also creates a transition |
| At most one persistence record per completed qualifying Stage 3 period | Consistent |
| Each ordinary Stage 3 alert does not independently create a cloud record | Consistent |
| Contact approval time is compared with the record period start | Exact match in the remote eligibility check |
| One unavailable sign permits two-channel assessment; fewer than two pauses assessment | Consistent |
| Insufficient lighting blocks calibration evidence and assessment | Consistent |
| Driver-zone selection is not facial identity recognition | Consistent |
| No vehicle control or medical diagnosis | Consistent with the inspected feature boundaries |

Primary implementation evidence: [calibration](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/calibration/CalibrationSession.kt:103), [temporal detectors](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/temporal/TemporalDetectors.kt:8), [warning stages and episode management](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:103), [two-channel gate](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriveAlertDetectionEngine.kt:108), [firmware track mapping](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/warning_output.h:93), and [sharing eligibility](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncRemoteDataSource.kt:19).

## Findings requiring correction or clarification

### F01 Infrared illumination contradicts the stated final prototype

**Priority: High. Type: Confirmed internal contradiction.**

Locations: Chapter I Scope and Limitations P0101 and P0105; Definition of Terms P0185; Chapter II synthesis P0288, P0332, P0336.

Scope explicitly excludes dedicated infrared LEDs and external illuminators and explains that the final prototype depends on available light. However, the IoT Device definition still includes “infrared illumination.” Chapter II repeatedly calls the camera “infrared-supported,” which can suggest active illumination despite the explicit exclusion. The comparison matrix's narrower “infrared-capable camera” wording is compatible with the scope.

**Correction:** Remove infrared illumination from the final IoT Device definition. Replace the three “infrared-supported” system descriptions with “an external infrared-capable OV3660 camera operating with available light and without dedicated infrared illumination.” Keep actively illuminated cameras in the discussion of other researchers' systems, clearly attributed to those systems.

The reported LED-distance and breadboard-power problems are historical experimental claims. Source code cannot establish that they occurred. Retain them only if supported by the team's hardware notes, photographs, measurements, or logs; this audit neither confirms nor disproves that history.

### F02 Eleven cited author groups have no matching bibliography entries

**Priority: High. Type: Confirmed academic traceability defect.**

The reference list P1601–P1661 has no matching entries for the following citations used in Chapters I and II:

| Missing cited work | Example location | Claim depending on it |
|---|---|---|
| Reyes 2025 | P0076 | International fatigue-related crash estimate |
| Ersoy et al. 2026 | P0237 | Personalized EAR threshold factor |
| Dong et al. 2026 | P0238–P0239 | Eye and mouth confirmation durations |
| Xu et al. 2025 | P0237 | Neutral forward head orientation reference |
| Kholerdi et al. 2016 | P0240–P0242 | 80% over three seconds |
| Schwarz et al. 2015 | P0251 and repeated later | Core escalation and abatement architecture |
| Gaspar et al. 2017 | P0252 and repeated later | Experimental staged-warning evidence |
| Chandana and Sangeetha 2023 | P0261–P0267 | Speech and external communication precedent |
| Jia et al. 2021 | P0264 | Assessment with partially obstructed features |
| Jia et al. 2022 | P0264 | Assessment with partially obstructed features |
| Awasekar et al. 2018 | P0265 | Highest-stage external communication |
| Fitzharris et al. 2017 | P0266 | External monitoring and feedback |

There are **11 author groups and 12 author-year works** in this table because Jia is cited for two years. Fitzharris appears as a coauthor of Horberry 2022 in the references, but that is not an entry for Fitzharris et al. 2017.

**Correction:** Add the exact full entries for works actually consulted, with verified authors, year, title, publication, and stable link/DOI. If a source cannot be identified, remove or replace its associated claim. Do not invent entries from surname/year alone. This is especially urgent for sources used to justify the actual threshold and warning-stage choices.

Online checks confirmed identifiable primary sources for Ersoy, Dong, Gaspar, and Kholerdi; details appear in the research-check section. This is a bibliography-completeness problem even when the underlying papers are real.

### F03 Highest Warning Stage has two different meanings in the system

**Priority: High. Type: Confirmed manuscript-to-dashboard discrepancy, with a plausible implementation defect.**

Locations: Chapter I P0114, P0171 and Highest Warning Stage definition P0183.

The definition says this is the highest stage reached during a monitoring session. The system does separately persist session maximum stage. However, the Driver dashboard insight calculates its highest stage from `AlertEvent.stage` across retained alert records. Those values are the stages at the times alerts were created, not all later stage transitions.

**Concrete case:** One continuous eye-closure episode creates a Stage 1 alert. Persistence can then advance to Stage 2 after 60 seconds and Stage 3 after another 60 seconds without creating a new distinct alert. Session highest stage becomes Stage 3, but the dashboard insight may still report Stage 1. Retaining or deleting other historical alerts can also change that aggregate.

Evidence: [dashboard insight calculation](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/postauth/AlertHistoryInsights.kt:25), [alert stage stored at detection](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/repository/AlertRepository.kt:62), [session maximum update](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:987).

**Correction:** Decide which metric the interface should provide. To preserve the manuscript definition, derive the dashboard metric from session highest-stage data and state the aggregation period. Otherwise label it “Highest warning stage recorded at alert creation” and distinguish it from session maximum stage. Do not assume that changing the thesis alone fixes the product discrepancy.

### F04 Warning Trigger omits actual repeated-warning requests

**Priority: High. Type: Incomplete operational definition.**

Locations: Definition of Terms P0197; Chapter II P0253–P0260.

The definition covers distinct events and stage-boundary persistence, but the application also requests current-stage reminders while a confirmed sign remains observable: every 2 seconds for eye closure and every 3 seconds for yawning/head down, measured from confirmation and reset around warning activation. These reminders are not new confirmed events and do not independently advance a stage or create a new Alert History row.

Evidence: [reminder scheduling](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/PersistentWarningRepeater.kt:11), [request intervals](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/PersistentWarningRepeater.kt:29), [delivery integration](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:902).

**Correction:** Add this third warning-request path to the definition and Chapter II intervention explanation. State that firmware may suppress reminders during playback reservations, so these are request intervals, not guaranteed audible start intervals. Keep the manuscript's correct statement that 60 seconds is a stage-evaluation interval.

### F05 Broad synchronization wording can imply that ordinary alerts are shared

**Priority: Medium. Type: Internal scope ambiguity.**

Locations: General objective P0093; specific dashboard objective P0099; Figure 1 dashboard bullet; Scope P0102.

These passages refer broadly to synchronizing “drowsiness-alert records,” and P0102 describes an “alert record” becoming eligible. Later passages correctly say that local individual alerts do not independently become synchronization records. The implementation creates separate Stage 3 boundary records; it does not upload the complete local Alert History.

Evidence: [local alert repository](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/repository/AlertRepository.kt:29), [separate synchronization input and repository](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncRepository.kt:20).

**Correction:** Use “records all distinct confirmed alert episodes locally and synchronizes only eligible Stage 3 transition and persistence records with approved Trusted Contacts.” Apply the distinction consistently to objectives, Figure 1, scope, and definitions.

### F06 Direct local Wi-Fi wording omits the actual provisioning and network topology

**Priority: Medium. Type: Architecture ambiguity, not proof that offline monitoring is false.**

Locations: P0097, P0169 and Local Offline Wi-Fi Connection P0186.

The current firmware joins Wi-Fi in station mode. Android sends network credentials through BLE. The operational local network can be the phone's hotspot or another shared local Wi-Fi network. “Direct local network” can be read as Wi-Fi Direct or as the camera creating its own access point; neither describes the inspected provisioning implementation.

Evidence: [station mode and connection](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/wifi_provisioning.cpp:66), [BLE protocol and lifecycle](C:/DriveAlert/drivealert-iot/PROVISIONING_PROTOCOL.md:1), [setup UI explanation](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/ui/postauth/DeviceSetupDetailScreens.kt:84).

**Correction:** Describe “a shared local Wi-Fi network, such as the Android phone's hotspot, with BLE-assisted network setup.” Explicitly distinguish Bluetooth provisioning, Wi-Fi video transport, Wi-Fi warning commands, and internet-based synchronization.

### F07 Face-loss assessment gating is confused with a delayed prompt

**Priority: Medium. Type: Timing ambiguity against the implementation.**

Location: Driver-zone paragraph P0106: “When no valid face is detected ... for a predefined duration, monitoring is paused.”

Loss of a valid selected-driver observation prevents reliable assessment immediately when recognized; the application also expires stale evidence. The visibility notification has a separate debounce period. The current `VisibilityAlertController` defaults are 3 seconds before notification and 2 seconds before clearing after recovery. Waiting for a prompt is not the same as allowing assessment during the waiting interval.

Evidence: [assessment permission](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:209), [missing-observation clearing](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriveAlertDetectionEngine.kt:84), [visibility debounce defaults](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/VisibilityAlertController.kt:63).

**Correction:** Say “Drowsiness-sign assessment pauses when reliable driver tracking is lost or observations become stale. A sustained visibility problem produces a separate delayed prompt. Assessment pauses do not automatically end the session.” Avoid calling this delayed face-loss behavior “monitoring” without distinguishing session continuity from assessment availability.

### F08 Speaker output is not limited to drowsiness-stage interventions

**Priority: Medium. Type: Material omission and overbroad wording.**

Locations: P0098, P0102, P0170, and P0250's claim that warning intervention begins only after a selected sign meets its confirmation condition.

The same physical speaker also receives requests for blocked eyes, blocked mouth, face not visible, insufficient lighting, and no movement. These messages can occur without a confirmed drowsiness event. They are separate guidance, not extra drowsiness signs or warning stages.

Evidence: [visibility speech intents](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/VisibilityAlertController.kt:47), [firmware guidance tracks](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/warning_output.h:62).

**Correction:** Qualify P0250 as “Drowsiness-stage warning intervention begins...” Add a short scope paragraph distinguishing drowsiness warnings from operational visibility/movement guidance. Speech being reserved for Stage 3 should specifically mean the **spoken rest advisory**, not all spoken output.

### F09 The movement advisory is missing from the stated current behavior

**Priority: Medium. Type: Implemented-feature omission.**

Locations: Chapter I scope and limitations; Chapter II system synthesis.

The current system displays “No movement detected” after 10 seconds of eligible inactivity and can request one spoken advisory at 25 seconds total. It reuses landmarks, gives drowsiness/visibility output priority, and does not create a drowsiness alert or alter warning stages. The thresholds are provisional engineering defaults.

Evidence: [movement configuration and behavior](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/DriverMovementTracker.kt:13).

**Correction:** Add a compact description if this feature is part of the evaluated build. Explicitly say it is not facial identification, validated liveness detection, replay prevention, or a fourth drowsiness sign. A photograph or replay can pass movement checks, and a stationary closed-eye photograph may still satisfy the drowsiness detector. If the study deliberately excludes this newer feature, name the evaluated build and exclusion.

### F10 The model architecture behind obstruction handling is not explained

**Priority: Medium. Type: Technical explanation gap.**

Locations: AI definition P0177; Chapter II P0234 and P0264.

The manuscript describes facial measurements and the effects of masks/sunglasses, but does not clearly identify the actual production split: MediaPipe Face Landmarker provides landmarks and transformation matrices; a separate FaceAttribNet TensorFlow Lite model supplies obstruction-related attributes. The sign decision layer uses calibrated numerical and temporal rules. No new drowsiness-classification model is trained by the inspected production pipeline.

Evidence: [production model initialization](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/face/MediaPipeFaceLandmarkerProcessor.kt:47), [model provenance](C:/DriveAlert/app/src/main/assets/FACE_ATTRIB_NET.md:1), [attribute hysteresis](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/face/FaceAttributePostProcessor.kt:67).

**Correction:** Add a short technical foundation paragraph identifying these roles. Do not imply that a generic MediaPipe landmark result alone establishes all supported occlusions, or that this team trained an AI model to classify physiological drowsiness.

### F11 All alerts are stored is a creation rule rather than a permanent-retention guarantee

**Priority: Medium. Type: Missing retention qualification.**

Locations: P0089, P0109, P0270–P0274 and record-review descriptions.

New distinct alert episodes are written locally, but users can delete them or enable age-based retention. The default is manual deletion. Shared-history removal is viewer-local and does not delete the Driver's Firestore source record. Monitoring Logs have separate retention and are not the same store as Alert History.

Evidence: [local alert cleanup and deletion](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/history/HistoryRepository.kt:51), [retention contract](C:/DriveAlert/HISTORY_RETENTION.md:1), [separate monitoring logs](C:/DriveAlert/MONITORING_LOGS.md:1).

**Correction:** Use “All distinct confirmed alert episodes are initially recorded locally, subject to user deletion and configured retention.” Explain that summaries describe retained records, not a complete lifetime history. Do not claim that deleting local Alert History retracts shared cloud records.

### F12 Overlapping episode grouping is described as optional when the code has a rule

**Priority: Medium. Type: Operational precision gap.**

Locations: P0244, P0253, P0260 and P0274 use “may” remain associated with one alert.

The controller checks whether a previously active episode sign is still among the currently confirmed signs. While that continuity remains, new confirmed signs are merged into the latest alert and do not create a new stage-advancing episode. When none of the prior episode signs remains confirmed, a later confirmation can become a new episode. Timestamp/type deduplication also rejects duplicate sign events.

Evidence: [exact episode merge rule](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:265).

**Correction:** Replace optional wording with the actual continuity rule. Distinguish a per-sign confirmation, a grouped alert episode, a warning request, and a Stage 3 boundary record. The four counts need not be equal. Do not introduce an arbitrary overlap timeout that is absent from the code.

### F13 Eye and mouth reset descriptions omit equality and unavailable input

**Priority: Low. Type: Exact boundary mismatch in explanatory wording.**

Locations: P0238 says EAR rises “above” threshold; P0239 says MAR returns “below.”

The eye candidate resets at EAR **greater than or equal to** its threshold. The yawn candidate resets at MAR **less than or equal to** its threshold. Invalid measurements interrupt candidates; higher-level tracking/lighting interruption clears evidence. Equality is therefore a recovery boundary, not a qualifying value.

Evidence: [EAR comparison](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/temporal/TemporalDetectors.kt:30), [MAR comparison](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/temporal/TemporalDetectors.kt:96).

**Correction:** Use “reaches or exceeds” for EAR recovery and “reaches or falls below” for MAR recovery. Also explain that unusable observations cannot accumulate qualifying duration. This matters for precise tests and pseudocode more than for the general introduction.

### F14 Head nodding means sustained downward posture in this implementation

**Priority: Medium. Type: Construct and recovery clarification.**

Locations: P0191–P0193 and Chapter II P0240–P0244.

The actual detector does not count repeated up/down nod cycles. It evaluates the fraction of valid observations above a downward pitch threshold in a mature rolling window. It requires at least two observations and three seconds since monitoring of that detector began. Recovery after confirmation requires a currently non-downward observation and a rolling downward ratio below 80%; one brief upward sample need not immediately re-arm it.

Evidence: [head-down window and re-arm](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/temporal/TemporalDetectors.kt:154).

**Correction:** Define the thesis term operationally as “sustained downward head posture, referred to in this study as head nodding.” State the rolling-ratio recovery condition. Do not equate 80% of observations with exactly 2.4 seconds continuously head down when frame cadence varies; the code uses an observation fraction, not duration weighting. The manuscript already says “valid observations,” which should be preserved.

### F15 Five valid seconds is not necessarily one uninterrupted five-second take

**Priority: Medium. Type: Method reproducibility clarification.**

Location: Calibration explanation P0237 and lighting limitation P0105.

The statement that rejected frames do not advance calibration time is correct. More precisely, an invalid observation breaks continuity between adjacent samples, but previously accumulated valid time and samples in the current phase are retained for ordinary interruptions. Completed phases can also be retained. A real target change or reset can invalidate unfinished work.

Evidence: [calibration valid-time accounting](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/calibration/CalibrationSession.kt:66), [tracking interruption versus target change](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriveAlertDetectionEngine.kt:208).

**Correction:** Say “Each phase accumulates at least five seconds across valid adjacent observation intervals; invalid intervals contribute no time. Certain target changes or explicit resets invalidate unfinished calibration.” Do not add “five uninterrupted seconds” unless the software is intentionally changed. The user also acknowledges each phase and receives preparation time, so the total interaction lasts longer than 20 seconds.

### F16 Evidence loss can break one physical behavior into separate observed episodes

**Priority: Medium. Type: Qualification missing from an otherwise correct deduplication claim.**

Locations: P0244 and P0253 say one continuous behavior will not generate repeated distinct events.

That is true while the application maintains usable continuous evidence. Tracking loss, stale frames, unavailable channels, or poor lighting can clear detection and episode evidence. If usable input returns while the physical behavior remains present, a fresh qualifying duration can eventually produce a new observed event because the system cannot establish continuity through the gap.

Evidence: [detector evidence clearing](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriveAlertDetectionEngine.kt:84), [camera-evidence clearing](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:1015).

**Correction:** Add “during uninterrupted valid observation.” Describe the distinction between a continuous real-world behavior and a continuous system-observed episode. Do not promise physical-episode deduplication across an interval the camera could not assess.

### F17 Lighting recovery wording is broader than the current recovery rule

**Priority: Low. Type: Edge-case precision issue.**

Locations: P0105 and P0249 say usable lighting must be sustained for the recovery period before assessment resumes.

This is appropriate for initial quality establishment and real poor-light recovery. The tracker also allows a new explicitly usable sample to restore a recently usable decision when the sample gap is no more than two seconds. It does not always restart a full two-second recovery after every brief missing sample.

Evidence: [freshness and recent-quality restoration](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/FaceLightingTracker.kt:38).

**Correction:** Use “resumes when the applicable freshness and lighting-recovery conditions are met.” Keep the simpler explanation in Chapter I and put exact thresholds in methods. Do not describe the brightness heuristic as a validated guarantee of measurement accuracy.

### F18 Trusted Contact approval wording omits bidirectional requests and revocation

**Priority: Medium. Type: Relationship-model omission.**

Locations: Trusted Contact definition P0194, synchronization P0195, P0275.

The system permits either role to initiate a connection-code request. The recipient approves or declines; the requester can cancel; either participant can revoke an approved relationship. “Selected and approved by the driver” does not fully describe both paths. Chapter I P0109 correctly says approval must remain active and should be the basis for the other descriptions.

Evidence: [relationship operations](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/connection/TrustedContactRepository.kt:20).

**Correction:** Define a Trusted Contact as “a registered user linked to a Driver through an approved connection request.” State that either side can initiate and revoke, and that authorization requires a currently approved relationship. Retain the record-period approval cutoff. This supports consent-based access without inventing identity recognition or emergency-response duties.

### F19 Offline operation needs prerequisites and background synchronization needs scheduling limits

**Priority: Medium. Type: Scope qualification.**

Locations: P0097, P0169, P0171 and P0275.

Local video processing and warning commands do not need internet, but starting monitoring requires a bound Driver account, a saved active calibration, configured/connected camera, confirmed alignment, and no unfinished calibration. Initial registration, cloud relationship operations and remote review need connectivity. Synchronization is scheduled with WorkManager, checked against the current authenticated UID, and retried on failure; internet becoming available is not a guarantee of immediate delivery.

Evidence: [monitoring prerequisites](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:513), [network constraints and backoff](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncWorker.kt:16).

**Correction:** Say “After the required account and device setup, active local monitoring can operate without internet. Eligible records are retained for background synchronization when connectivity, authentication, relationship eligibility, and operating-system scheduling permit.” Do not describe all app features or first-time setup as offline.

### F20 Dashboard platform and role boundaries are underspecified

**Priority: Medium. Type: Feature-scope ambiguity.**

Locations: Dashboard objective P0099, Driver significance P0114, and Figure 1 explanation P0171.

The Android Driver interface offers local monitoring and Alert History. The React web portal is for Trusted Contacts reviewing eligible synchronized Stage 3 records and managing relationships. It does not expose live video, Driver local Alert History, device controls, or remote monitoring controls. The manuscript's generic “dashboard” wording does not make these distinctions clear.

Evidence: [web portal scope](C:/DriveAlert/drivealert-web/README.md:1), [web routing](C:/DriveAlert/drivealert-web/src/app/router/AppRouter.tsx:1).

**Correction:** Identify “Android Driver dashboard” and “Trusted Contact Android views and web portal” separately. If the study intentionally evaluates only some interfaces, explicitly name them. Do not imply that a Trusted Contact can inspect the Driver's full local history or view the camera feed.

### F21 Synchronized records and Trusted Contact notifications are different features

**Priority: Medium. Type: Implemented-path omission and delivery precision.**

Locations: P0089, P0115 and P0275.

The repository contains an Android FCM receiving service and a Cloudflare Worker notification path. Synchronization writes records to Firestore; a separate dispatch process can notify Android Trusted Contacts. The web portal explicitly has no Trusted Contact push notifications. Source presence does not prove that the Worker URL, credentials, deployment, device registration, and permissions are configured on the deployed system.

Evidence: [Android message receiver](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/notification/DriveAlertMessagingService.kt:25), [worker deployment requirements](C:/DriveAlert/notification-worker/README.md:1), [sync and dispatch separation](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncWorker.kt:21).

**Correction:** State whether the evaluated build includes this notification path. If included, distinguish successful record synchronization from notification dispatch and from the contact actually seeing a notification. The manuscript's statement that this is not an emergency-notification service remains appropriate.

### F22 A successful warning command does not prove audible playback

**Priority: Medium. Type: Evaluation and delivery claim qualification.**

Locations: Alarm objective P0098; Scope P0102; Flow P0170.

The Android gateway marks a warning delivered after HTTP 200 with an accepted response. That establishes device command acceptance. It does not measure speaker sound pressure, verify the correct microSD recording, detect a disconnected speaker, or prove that the Driver heard the alert. Firmware uses a DFPlayer and prerecorded tracks, and playback arbitration can affect audible timing.

Evidence: [HTTP acceptance criterion](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/device/HttpWarningOutputGateway.kt:58), [firmware output implementation](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/warning_output.cpp:151), [warning transport contract](C:/DriveAlert/drivealert-iot/WARNING_OUTPUT_PROTOCOL.md:1).

**Correction:** Retain speaker output as an objective or intended function, but validate actual sound separately before writing results as established performance. Separate sign-confirmation time, Android request time, ESP32 acceptance, playback onset, track correctness, and audibility. Adjustable minimum volume is implemented logically as DFPlayer levels 26/28/30; this is not a calibrated minimum decibel guarantee.

### F23 Comparative matrix marks unsupported absence too confidently

**Priority: Medium. Type: Evidence-quality problem and currentness risk.**

Locations: Chapter II related-system paragraphs P0281–P0288; Tables 1.1–1.3; Table 2 summary and P0336.

A cross for a competitor feature needs positive evidence that the feature is absent or a clearly defined reviewed scope. A product page that does not mention history or sharing does not prove absence. Similarly, a broad check for WingDriver “stores drowsiness-alert records” needs a definition of which product/version and what qualifies as a record.

Current official WingDriver material describes fleet dashboards, analytics, sharing controls and per-active-driver subscriptions. It does not by itself substantiate the matrix's free individual app claim. The current Fatigue Alert listing mentions session statistics, which is not automatically equivalent to durable alert history but shows why a binary cross needs a precise definition. These current observations do not establish what the July 2026 pages said. Sources: [WingDriver official site](https://wingdriver.com/), [Fatigue Alert official listing](https://apps.apple.com/us/app/fatigue-alert-stay-awake/id6479893638).

**Correction:** Use “documented,” “not documented in the reviewed sources,” and “outside the reviewed product tier” where appropriate. Date the comparison and identify versions, platforms and product tiers. Preserve July claims only with July evidence; otherwise update the retrieval date and claims together. Define “Trusted Contact synchronization” narrowly so generic fleet sharing is not falsely equated with DriveAlert's relationship model. Describe integration as the project's contribution rather than claiming no other system combines these functions.

### F24 Study evaluation and editorial status need a consistent frame

**Priority: Medium for research validity; Low for wording. Type: Evaluation gap and editorial inconsistencies.**

Locations: Objective P0095, controlled-testing Scope P0103/P0111, calibration-testing P0245, proposal wording P0080, final-prototype statements P0101/P0105, and related-study references.

The survey objective addresses occurrence of drowsiness “while driving,” while evaluation is limited to controlled or simulated conditions. These can coexist if the survey is retrospective and technical trials are controlled, but the distinction is not explicit. Demonstrating an eye closure or mouth opening tests sign recognition; it does not independently establish physiological drowsiness. Survey answers do not validate EAR/MAR thresholds, and temporal confirmation does not eliminate sustained speech, deliberate eye closure or downward-looking false positives.

**Correction:** Distinguish survey evidence, scripted sign demonstrations, functional integration testing, and any independent ground truth. Define false alerts, missed signs, eligible observation time, assessment-unavailable time, warning latency and hardware playback checks. Do not treat paused/obstructed intervals as successful negative detections. No accident-reduction or clinical claim should be added; the manuscript already correctly excludes those claims.

Also fix these concrete editorial issues:

- P0171: “highest Warning Stage reachedf” → “highest Warning Stage reached.”
- P0279: “Related Studes” → “Related Studies.”
- P0180: add the missing final period.
- P0233 cites Jan et al. 2022, while the reference entry P1621 is Jan et al. 2023. The title refers to FTC 2022, but the proceedings publication is listed as 2023. Confirm the publisher's bibliographic year and use it consistently.
- P0232 cites NHTSA 2023, while the bibliography's relevant NHTSA page P1656 is undated. Align the in-text citation with the exact dated report or undated webpage actually used.
- Decide whether this is a proposal, an implementation-stage manuscript, or a completed-study report. Future tense for planned evaluation is reasonable; future tense for functions already being described as the final prototype needs an explicit status date or consistent revision. Do not turn pending validation into completed results simply by changing tense.

## Detailed operational distinctions to preserve

### Event counts and warning counts

The implementation has several separate units:

1. A **sign confirmation** is emitted by an individual detector.
2. An **alert episode** groups qualifying signs according to episode continuity and is stored in local Alert History.
3. A **warning request** asks the device for an initial, stage-change, recurrence, or reminder output. Multiple requests can occur for one alert episode.
4. A **Stage 3 boundary record** is generated on transition or at a qualifying persistence boundary. It is independently evaluated for sharing.
5. A **notification dispatch** is a separate delivery action; dispatch grouping can differ from record count.

The thesis should not use “event,” “alert,” “warning,” “notification” and “synchronization record” interchangeably. The current warning-controller code deliberately separates them.

### Stage 3 period timestamps and approval cutoff

On progression from Stage 2 to Stage 3, the transition record is built from the outgoing Stage 2 period. Its period start can therefore be earlier than the instant Stage 3 begins. The sharing check compares approval time against this stored period start, not merely the instant of Stage 3 entry. A contact approved halfway through that Stage 2 period can be ineligible for the transition record even if approval preceded the Stage 3 warning. Later eligible Stage 3 persistence periods can still be shared.

Evidence: [transition record construction](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:115), [period-based approval comparison](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncRemoteDataSource.kt:31).

P0109's period-based wording is broadly correct, but this edge case should be explicit in methodology and acceptance tests. “Initially enters Stage 3” should also mean each transition into Stage 3, including re-entry after de-escalation, rather than only the first such transition in a session.

The boundary `eventCount` is not universally a count of newly stored alert rows. The state machine seeds the next period with one count when persistent signs carry forward. Do not label every boundary count as “number of new distinct alerts in this minute” without checking that definition.

### What an assessment pause does to warning stages

The session can remain active while detection evidence is unavailable. Tracking or lighting loss clears active sign evidence and stops reminders. The stage timer continues to be ticked; existing Stage 3 recurrence recorded before a pause can still satisfy a completed period's recurrence rule. Therefore neither “everything freezes during a pause” nor “a pause immediately resets the stage to normal” accurately describes the current controller. Chapter I correctly says that an assessment pause does not by itself end a session; methods should describe the timer behavior as well.

### Driver zone selection is more than ignoring passengers

The selector requires a single eligible face whose center lies inside the region and whose bounding box has at least 80% area inside it. It rejects competing faces and discontinuous target associations. An outside passenger can still cause ambiguity if boxes overlap sufficiently; the system does not simply ignore every face outside the guide. Stable acquisition is also required. This is spatial association, not identity verification.

Evidence: [selection and competing-face rules](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/face/DriverFaceSelector.kt:95).

### Calibration and obstruction declarations

The current interface permits accessory declarations such as mask or sunglasses at monitoring start and can acknowledge expected obstructions. These declarations affect availability/guidance; they do not turn an unavailable facial channel into reliable evidence. The four calibration phases still require their corresponding measurable features. Do not imply that a completely blocked mouth can supply a valid mouth-open calibration just because monitoring can later operate with yawning unavailable.

## Selected primary-source checks

These checks help distinguish unsupported bibliography from unsupported numerical choices. They are deliberately limited to the claims verified here.

| Source | What was checked | Result and manuscript implication |
|---|---|---|
| [Ersoy et al. 2026](https://arxiv.org/html/2604.22479v1) | Personalized EAR factor | The experimental setup gives 75% of the open-eye calibration baseline. Supports that factor; it is an arXiv preprint and is missing from the bibliography. Its MAR method differs from DriveAlert's open-mouth method. |
| [Dong et al. 2026](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0346055) | Eye/mouth duration justification | The paper supports approximately two- and three-second separation points, with strict greater-than triggers. DriveAlert uses at least two/three seconds; identify this as an adaptation at the equality boundary. Missing bibliography entry. |
| [Quiles-Cucarella et al. 2024](https://www.mdpi.com/1424-8220/24/17/5683/pdf?version=1725096399) | Personalized initialization | Indexed publisher text confirms five-second maximum-mouth-opening initialization and a three-quarter EAR reference. Direct publisher access was rate-limited during portions of this check; the exact MAR-factor and yawn-duration attribution should receive a final full-text verification before submission. The source is present in the bibliography. |
| [Gaspar et al. 2017](https://www.tandfonline.com/doi/abs/10.1080/15389588.2017.1303140) | Sample size and staged intervention | Confirms 72 participants, six-second state assessments and 60-second countermeasure-state boundaries. Supports the manuscript's distinction between its event-based adaptation and the source's state classifier. Missing bibliography entry. |
| [Kholerdi et al. 2016](https://eclectx.org/Publications/2016_DriverDrowsiness_PublishedPaper.pdf) | Head-position persistence | The author-hosted paper includes the 80% over three seconds head-position rule. It considers dropped or away-from-road head position and combines other cues; DriveAlert's pitch-only mapping remains an adaptation. Missing bibliography entry. |

The exact primary Schwarz 2015 proceedings file was not accessible through the attempted publisher/archive URL. Its identity is corroborated by the reference list of the primary Gaspar article, but that does not replace direct verification of every detailed rule attributed to Schwarz. Xu, both Jia works, Chandana and Sangeetha, Awasekar, Fitzharris 2017 and Reyes were not fully identified and verified in this pass. Their bibliography repair remains required.

Two additional source-fit cautions:

- P0079 cites an ESP-AT web-server example for ESP32-C3 while the actual firmware uses a classic ESP32-CAM and Arduino/C++ APIs. That example can support a broad local-network concept, but it is not the implementation-specific reference for the actual camera and HTTP server. Cite the appropriate classic ESP32/esp32-camera/Arduino documentation for methods.
- P0276 groups Murphy's patient-safety dashboard review and Moens's predictive-maintenance dashboard work as support for recorded safety-event review. These are cross-domain interface precedents, not validation of driver-drowsiness insight accuracy or road-safety outcomes. Say explicitly what is being transferred: visualization and review of stored events.

## Repository inconsistencies found while checking the manuscript

These are **not instructions to alter the thesis to match stale text**. Production code was preferred over high-level documentation.

1. **Stage 2 advisory field:** `warningOutputCommand` currently puts `REST_ADVISORY` into the command model for both Stages 2 and 3. The HTTP payload omits advisory text, and firmware selects ordinary tracks for Stage 2, so this does not establish Stage 2 physical speech. The manuscript's sound-only Stage 2 agrees with the current physical transport mapping. Correct the command model separately to prevent future consumers from interpreting it incorrectly. Evidence: [command construction](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:326), [wire fields](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/device/HttpWarningOutputGateway.kt:82), [track mapping](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/warning_output.h:93).
2. **Stage 2 UI description:** `WarningStage.STAGE_2` is labeled “Stronger Warning,” although the current physical sound selection is the same as Stage 1. Interpret stronger as intervention-state progression only, or revise the UI copy. Do not claim louder Stage 2 audio without another implemented change. Evidence: [stage labels](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/postauth/PostAuthModels.kt:8).
3. **Stale product document:** `PRODUCT.md` still calls the system an offline UI prototype with simulated records, while production source and README implement real services and monitoring. It is not a reliable current feature baseline. Evidence: [product document](C:/DriveAlert/PRODUCT.md:1), [current README](C:/DriveAlert/README.md:1).
4. **Detection documentation drift:** `app/DRIVEALERT_DETECTION.md` lists one face and older visibility timings. Production processor defaults to four face candidates; current visibility defaults are 3 seconds to notify and 2 seconds to clear. Use production source when specifying the evaluated build. Evidence: [face capacity](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/face/MediaPipeFaceLandmarkerProcessor.kt:35), [visibility timings](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/VisibilityAlertController.kt:63).

The README also says the live Firestore project has test-mode rules while reviewed rules are checked in. This audit did not inspect the live deployment. Do not describe authorization as production-secure solely because the local rules implement relationship checks; verified deployment is a separate requirement.

## Suggested replacement passages

These passages can be adapted into the manuscript after the evaluated build and study status are fixed. They are recommendations, not edits already made to the Word file.

### IoT Device definition

“IoT Device refers to the DriveAlert hardware unit containing an infrared-capable OV3660 camera, ESP32-based wireless communication, and a DFPlayer-driven onboard speaker. The final prototype does not include dedicated infrared illumination and relies on available light.”

### Local Offline Wi-Fi Connection definition

“Local Offline Wi-Fi Connection refers to the shared local Wi-Fi network, such as the Driver's Android hotspot, through which the ESP32-CAM transmits video and receives warning commands. BLE is used to provision network credentials. Local monitoring does not require internet access after the required account and device setup.”

### Dashboard objective

“To develop an Android Driver dashboard that records and displays distinct confirmed alert episodes and descriptive summaries, and Trusted Contact interfaces that display only eligible synchronized Stage 3 transition and persistence records under an approved relationship.”

### Warning Trigger definition

“Warning Trigger refers to a request to activate the output associated with the current Warning Stage. Requests may follow a new distinct confirmed alert episode, a qualifying stage-boundary transition or continuation, or a scheduled reminder while a confirmed sign remains observable. Reminder requests do not independently create new alert episodes or advance the Warning Stage, and audible timing depends on device playback availability.”

### Assessment availability and non-drowsiness guidance

“DriveAlert pauses drowsiness-sign assessment when reliable driver tracking, usable lighting, or the required number of available sign channels is absent. These pauses do not by themselves end the monitoring session. Separate visibility and movement messages guide the Driver to restore usable camera input; they do not constitute drowsiness alerts or additional Warning Stages.”

### Local records and external sharing

“Distinct confirmed alert episodes are initially stored in the Driver's local Alert History, subject to user deletion and configured retention. External sharing uses separate Stage 3 transition and persistence records rather than uploading the complete local history. Sharing depends on the record's period start, an approved relationship, internet availability, authentication and background-work scheduling.”

### Technical processing foundation

“DriveAlert uses MediaPipe Face Landmarker to obtain facial landmarks and head-orientation information from the external camera stream. The Android application applies Driver-specific EAR and MAR thresholds and sign-specific temporal rules to these measurements. A separate FaceAttribNet model supports the implemented obstruction checks. These processes identify selected observable conditions and do not medically establish drowsiness.”

### Operational head-nodding definition

“Head nodding refers operationally to sustained downward head posture relative to the Driver's calibrated neutral orientation. The detector uses a fixed downward deviation greater than 15 degrees and an observation-based persistence condition of at least 80% within a rolling three-second window. It does not count repeated nod cycles or determine the cause of the posture.”

## Recommended revision order

1. Fix F01 and F02 before submission: contradictory final hardware descriptions and missing primary design references are the clearest defense vulnerabilities.
2. Resolve F03 as a product/manuscript decision; do not silently rename a session metric while leaving the dashboard behavior unexplained.
3. Update the warning-request, event-grouping and record-sharing vocabulary together, especially F04, F05, F08 and F12.
4. Name the evaluated Android, firmware and web builds. Include or explicitly exclude newer visibility, movement, retention, log and notification functions.
5. Clarify provisioning, prerequisites, assessment pauses and recovery without promising instantaneous synchronization or guaranteed playback.
6. Repair the comparative matrix's evidence labels, publication-year inconsistencies and the two visible typos.
7. Verify every missing reference and the remaining precise numerical attribution against full primary texts. Then refresh Word's contents/captions/page references and perform a separate visual-layout review.

**Completion status:** The content-to-source audit is complete for the stated scope. The original document is unchanged. Manuscript revision, physical validation, live deployment verification, exhaustive bibliography fact-checking, and visual pagination review remain separate work.

# DriveAlert Chapters 1 and 2 audit of the recovered document

November 2, 2026

**The recovered document is materially better aligned with the current system than the file examined earlier. The previous report is superseded by this report.** The main infrared definition, the two meanings of Highest Warning Stage, Trusted Contact access boundaries, visibility-guidance classification, and eye/mouth reset comparisons have been corrected. They must not be presented as unchanged defects.

The core calibration, temporal detection and warning-stage mechanics remain substantially consistent with production source. The most consequential remaining issues are the revised face-processing description, missing bibliography entries, omission of ongoing warning reminders, and broad sharing language in the objectives and Figure 1. There is also one remaining ambiguous infrared phrase in Chapter II's final comparison summary.

This report separates confirmed inconsistencies from feature omissions, methodological precision and editorial corrections. An omitted detail is not automatically a contradiction, and Chapters 1 and 2 do not need to document every implementation constant.

## Document and audit boundaries

- Source: `C:\Users\Admin\Downloads\DriveAlertlatest.docx`.
- File size at audit: 9,568,012 bytes.
- SHA256: `1c0780da89f077c483a079c73ca348b716c41073325d60cf18c22fc1b46223fa`.
- Chapter I: top-level paragraphs P0073–P0177.
- Chapter II: top-level paragraphs P0205–P0305, including the comparative tables.
- References consulted: P1563–P1625.
- The editable Figure 1 text was inspected, including its dashboard bullet.

The P identifiers are zero-based positions in Word's top-level paragraph collection, not printed page numbers. Find passages through the section title and the quoted phrase. The original document was not edited. No application code was changed.

The audit compares the recovered manuscript with the current working tree under `C:\DriveAlert`, including production Android detection and monitoring code, firmware transport and audio mapping, local persistence, synchronization, and the Trusted Contact portal. Source inspection does not prove which build is installed on the phone or ESP32, that an external service is deployed, or that physical playback succeeds. No new build, hardware trial, road test, live cloud authorization test, or automated test suite was run for this manuscript audit.

Visual pagination and layout have not been verified. The prior renderer attempt failed because `soffice.exe` is unavailable, and the selected Windows dependency bundle has no LibreOffice. This report therefore makes no page-specific or rendered-layout claims. Word content and figure text were examined structurally. Duplicate text from alternate OOXML shape representations was not treated as proof that Word visibly duplicates the figure.

## Earlier findings that are resolved or substantially corrected

| Earlier concern | Evidence in the recovered document | Current verdict |
|---|---|---|
| IoT Device definition included infrared illumination despite its exclusion | P0164 now says infrared-capable camera and no longer lists illumination | Resolved at that definition |
| Multiple system descriptions said infrared-supported | P0079, P0269 and P0301 now use camera-capability wording | Corrected there; one residual phrase remains at P0305 |
| Highest Warning Stage was defined solely as session maximum | P0162 explicitly distinguishes session maximum from the highest stage attached to stored warning events; P0114 makes the same distinction | Substantively resolved against current calculations |
| Scope described an ordinary alert becoming synchronization eligible | P0102 explicitly names separate Stage 3 transition and persistence records | Resolved in that scope paragraph |
| Trusted Contact definition implied access to general insights/history | P0173 limits access to eligible shared Stage 3 records and explicitly excludes complete local history/full Driver insights | Resolved |
| Approval could appear to release earlier complete history | P0174 and P0256 explicitly state the period-start cutoff and continued approval requirement | Resolved at the stated contract level |
| Face loss was described as permitting assessment until a delay expired | P0106 now says assessment pauses when reliable tracking is unavailable | Timing confusion corrected; a different face-count issue was introduced |
| All warning intervention appeared to require a confirmed drowsiness sign | P0231 now distinguishes drowsiness-stage intervention from operational visibility/lighting guidance | Resolved for visibility and lighting guidance |
| Eye and mouth reset wording omitted equality | P0219 says reaches or rises above EAR threshold; P0220 says reaches or falls below MAR threshold | Resolved |
| Jan citation year mismatch | P0214 now cites Jan et al. 2023, matching P1585 | Resolved |
| Related Studes typo | P0260 is Related Studies | Resolved |
| Scope implied warning output was necessarily successful | P0102 now says requests the corresponding warning output | Improved; physical playback remains an evaluation task |
| Comparison matrix's infrared row was broad | Table 2 now says a dedicated external infrared-capable monitoring camera | More precise and consistent with the stated hardware category |

**Correction to my previous recommendation:** The earlier suggestion that the dashboard must be changed to match a session-only definition is no longer necessary for manuscript consistency. The recovered definition deliberately documents the two different metrics. A product redesign is optional; the remaining manuscript issue is consistent naming of those metrics, addressed in R06.

## Remaining findings

### R01 Face detection and Driver selection are conflated

**Priority: High. Classification: Confirmed description mismatch or ambiguity against current production configuration.**

**Where:** Chapter I, Scope and Limitations, P0106, paragraph beginning “DriveAlert uses guided facial-measurement calibration...”

**Current wording:** “The current system processes one detected face and does not guarantee automatic exclusion of passengers or other faces entering the camera view.”

**What the current implementation does:** MediaPipe is configured with a default maximum of four face candidates. The production ViewModel constructs that processor without overriding the default. A separate DriverFaceSelector selects at most one eligible Driver face using the configured Driver area and spatial continuity. Competing or ambiguous faces pause assessment. Four candidates reaching the configured capacity also cause ambiguity rather than silently choosing one.

Evidence: [processor capacity and MediaPipe options](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/face/MediaPipeFaceLandmarkerProcessor.kt:34), [production construction](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:171), [eligibility and ambiguity checks](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/face/DriverFaceSelector.kt:95).

**Why it matters:** If “one detected face” describes the inference configuration, it is incorrect. If it means one selected face is assessed, it is defensible but too imprecise. The paragraph also removes the implemented Driver-area selection mechanism from its explanation. Guided measurement calibration and spatial Driver selection are separate operations; both exist.

**Recommended replacement:** “DriveAlert uses guided facial-measurement calibration and spatial Driver-area selection rather than facial recognition. The face-processing pipeline may detect multiple face candidates, but drowsiness-sign assessment uses at most one selected Driver face. Selection depends on the configured Driver area and spatial continuity; ambiguous, competing or unreliable detections pause assessment. These rules do not verify identity or guarantee passenger exclusion in every scene.”

Do not claim that every outside passenger is ignored. The selector also considers competing overlapping boxes, malformed candidates and target continuity. Conversely, do not describe the system as having no Driver-area safeguards.

### R02 Central design citations still lack bibliography entries

**Priority: High. Classification: Confirmed academic traceability defect.**

**Where:** Chapter I P0076; Chapter II P0218–P0258; references P1563–P1625.

The recovered bibliography still lacks the following **12 author-year works across 11 author groups**:

| Cited work missing from the bibliography | Example location | What it supports in the manuscript |
|---|---|---|
| Reyes 2025 | P0076 | International fatigue-related accident estimate |
| Ersoy et al. 2026 | P0218 | Personalized EAR factor |
| Dong et al. 2026 | P0219–P0220 | Two- and three-second confirmation choices |
| Xu et al. 2025 | P0218 | Neutral forward head reference |
| Kholerdi et al. 2016 | P0221–P0223 | Head persistence criterion |
| Schwarz et al. 2015 | P0232 and following | Core staged escalation/abatement mechanics |
| Gaspar et al. 2017 | P0233 and following | Experimental staged-warning support |
| Chandana and Sangeetha 2023 | P0242–P0248 | Sound, speech and external communication precedent |
| Jia et al. 2021 | P0245 | Operation with partially obstructed features |
| Jia et al. 2022 | P0245 | Operation with partially obstructed features |
| Awasekar et al. 2018 | P0246 | Highest-stage contact communication |
| Fitzharris et al. 2017 | P0247 | External monitoring/feedback evidence |

Fitzharris being a coauthor of Horberry 2022 at P1584 does not provide the missing Fitzharris 2017 entry.

**Why it matters:** The reader cannot trace the primary works underlying the implemented numerical rules and intervention architecture. This does not mean the works are fictitious. Several were identifiable through primary sources in this audit.

**What to do:** Add full verified entries for the exact works consulted, or replace/remove the dependent claim if the work cannot be identified. Do not invent bibliographic details from surname and year. Citation repair should precede final submission.

### R03 Warning Trigger still omits the reminder pathway

**Priority: Medium. Classification: Material operational omission.**

**Where:** Definition of Terms, Warning Trigger, P0176; Chapter II P0234–P0241 explaining continuous episodes and stage boundaries.

**Current wording:** P0176 names new distinct events, boundary persistence and Stage 3 recurrence. It does not mention scheduled current-stage reminder requests.

**System behavior:** While a confirmed sign remains observable, Android schedules reminders every two seconds for eye closure and every three seconds for yawning or head down. These requests do not create new distinct alerts, independently advance stages, or independently create Stage 3 synchronization records. Stage-change warnings supersede nearby reminders, and firmware playback arbitration can make audible repetitions less frequent than requests.

Evidence: [reminder intervals](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/PersistentWarningRepeater.kt:29), [controller integration](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:247), [separate firmware playback handling](C:/DriveAlert/drivealert-iot/WARNING_OUTPUT_PROTOCOL.md:1).

**Why it matters:** The correct statement that continuous behavior does not create repeated distinct events must not imply that no further sound requests occur until 60 seconds. Alert episodes and warning requests have different counts.

**Suggested addition:** “A warning may also be reissued as a current-stage reminder while a confirmed sign remains observable. Such requests do not create additional distinct alert episodes or independently advance the Warning Stage. Actual audible repetition depends on device playback availability.” Put exact request intervals in the technical discussion or methods.

### R04 Objectives and Figure 1 still describe sharing too broadly

**Priority: Medium. Classification: Incomplete propagation of an otherwise corrected sharing definition.**

**Where:** General objective P0093; dashboard objective P0099; Figure 1's dashboard bullet; broad system introduction P0091.

**Current wording:** P0093 says the system stores drowsiness-alert records and synchronizes “these records.” P0099 says the dashboard “synchronizes drowsiness-alert records.” The Figure 1 bullet uses the same broad category.

**What is now correct elsewhere:** P0102, P0109, P0173, P0174, P0251–P0256, P0269 and P0301 explicitly distinguish local warning events from separate shared Stage 3 records.

**Why it matters:** A panel reading the objectives alone could still expect upload of the individual alert history. The objectives should agree with the now-correct detailed scope.

**Suggested replacement for the dashboard objective:** “To develop a dashboard that records and displays distinct confirmed alert episodes locally and supports synchronization of eligible Stage 3 transition records and persistence updates for approved Trusted Contact review.”

Apply the same record categories to the general objective and Figure 1. Do not remove the corrected detailed paragraphs. Evidence of separate storage boundaries: [local alert repository](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/repository/AlertRepository.kt:29), [Stage 3 repository](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncRepository.kt:20).

### R05 One residual infrared phrase remains in the final summary

**Priority: Low. Classification: Residual ambiguous terminology, not the former direct hardware contradiction.**

**Where:** Chapter II, Comparative Matrix Summary, P0305, paragraph beginning “Table 2 presents...”

**Exact phrase:** “an external infrared-supported IoT camera.”

The explicit IoT Device definition is now fixed. P0101 and P0105 exclude illumination; P0230 clearly explains that infrared capability does not provide light; P0269 and P0301 use capability wording. Only P0305 retains the ambiguous earlier description within the audited chapter text.

**Correction:** Replace the phrase with “an external IoT camera with an infrared-capable sensor and no dedicated infrared illumination.” This makes the final synthesis agree with the scope. This finding should not be reported as though the recovered definition still includes IR LEDs.

### R06 Highest-stage distinction is fixed but not used consistently in the Flow paragraph

**Priority: Low. Classification: Residual terminology and typo.**

**Where:** Flow of the Study, P0150, dashboard description; also broad rationale P0088.

P0150 still contains “highest Warning Stage reachedf.” P0114 and P0162 now correctly distinguish the highest stage attached to retained warning events from the highest stage reached during a session.

**Why it matters:** The unrevised Flow wording can reintroduce the session-maximum interpretation for the alert-history dashboard. The dashboard calculation still uses the maximum stage attached to stored alerts; the session separately stores its actual maximum.

Evidence: [history insight calculation](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/postauth/AlertHistoryInsights.kt:25), [session maximum persistence](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:987).

**Correction:** Change the Flow phrase to “highest stage associated with retained warning events.” Mention session highest stage separately if needed. Remove the stray `f`. Use the same distinction in P0088. The recovered definition itself needs no reversal.

### R07 Record creation and retention remain undifferentiated

**Priority: Medium. Classification: Implemented-feature omission affecting interpretation of summaries.**

**Where:** P0089, P0109 and P0251–P0257 state that events are stored and available for review.

Those statements are correct as initial recording behavior. The software also supports manual deletion and age-based retention. Shared-history removal is a viewer-local visibility action rather than deletion of the Driver's cloud source record. Monitoring Logs and Stage 3 delivery records are separate from ordinary Alert History.

Evidence: [local deletion and retention implementation](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/history/HistoryRepository.kt:51), [retention contract](C:/DriveAlert/HISTORY_RETENTION.md:1).

**Why it matters:** “All events are stored” should not imply that every event remains visible forever or that dashboard summaries cover a lifetime history. Deleting a local alert also should not be described as withdrawing previously shared cloud records.

**Suggested addition to scope:** “Distinct confirmed alert episodes are initially stored locally, subject to user deletion and configured retention. Descriptive summaries reflect retained records. Personal removal of shared history does not delete the source Driver's cloud records.”

### R08 The movement advisory is still absent

**Priority: Medium if the current build is evaluated. Classification: Implemented-feature omission.**

**Where:** Scope P0101–P0111 and Chapter II system-specific warning discussion P0231 onward.

P0231 now correctly mentions visibility and lighting guidance, but not the separate inactivity/movement feature. The active implementation shows No movement detected after ten seconds of eligible inactivity and can request one spoken movement advisory at 25 seconds total. Drowsiness warnings and visibility guidance have priority; failure does not automatically cause repeated retries in the same episode.

Evidence: [movement thresholds](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/DriverMovementTracker.kt:13), [production integration](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:211).

**Why it matters:** Participants may encounter this output during an evaluation although the paper describes only three sign channels and visibility guidance. It should not be mistaken for a fourth sign, an additional Warning Stage or proof of drowsiness.

**Correction:** Add one scope sentence and briefly mention it alongside operational guidance in P0231. State that its settings are provisional and that it is not identity verification or validated liveness/replay detection. If the evaluated build excludes the feature, explicitly identify that build and exclusion rather than describing current source as if the feature did not exist.

### R09 The production AI model roles remain unexplained

**Priority: Medium. Classification: Technical foundation gap.**

**Where:** AI definition P0156; facial-feature literature P0215; supported mask/sunglasses behavior P0245.

The system uses MediaPipe Face Landmarker for landmarks and facial transformation matrices, rule-based calibrated EAR/MAR and pitch-temporal decision logic, and a separate FaceAttribNet TensorFlow Lite model for supported obstruction attributes. The paragraph describing mask/sunglasses behavior does not identify how this information is produced.

Evidence: [MediaPipe and attribute model initialization](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/face/MediaPipeFaceLandmarkerProcessor.kt:47), [attribute model provenance](C:/DriveAlert/app/src/main/assets/FACE_ATTRIB_NET.md:1), [availability filtering](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriveAlertDetectionEngine.kt:100).

**Why it matters:** Readers could attribute every visibility judgment to landmark geometry or assume that the project trained a drowsiness classifier. The inspected implementation supports neither interpretation.

**Suggested addition:** “MediaPipe Face Landmarker provides facial landmarks and head-orientation information. DriveAlert applies calibrated measurement thresholds and temporal rules to these outputs. A separate FaceAttribNet model supports the implemented obstruction checks. The system does not medically classify drowsiness or train a new drowsiness model within this pipeline.”

### R10 Local networking is underspecified and instant processing is overstated

**Priority: Medium. Classification: Architecture precision and unsupported latency wording.**

**Where:** Local Offline Wi-Fi Connection P0165; Flow P0148.

P0148 says the connection allows video to be processed “instantly.” P0165 calls the link a “direct local network.” The implementation uses BLE to provision Wi-Fi credentials; ESP32 joins a hotspot or shared local Wi-Fi in station mode; video and warning commands use local HTTP. This is not a documented Wi-Fi Direct implementation, and latency is nonzero and device-dependent.

Evidence: [station mode](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/wifi_provisioning.cpp:66), [BLE provisioning contract](C:/DriveAlert/drivealert-iot/PROVISIONING_PROTOCOL.md:1), [warning HTTP gateway](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/device/HttpWarningOutputGateway.kt:35).

**Correction:** Use “processed locally in near real time, subject to device and network performance.” Define the connection as a shared local Wi-Fi network, such as the phone's hotspot, with BLE-assisted setup. Distinguish setup transport, video transport, warning control and internet synchronization.

### R11 Offline capability and synchronization need their operational conditions

**Priority: Medium. Classification: Scope qualification.**

**Where:** Offline objective P0097; Flow P0148/P0150; synchronization definition P0174; offline queue discussion P0256.

Local detection and warning transport need no internet after setup, but starting monitoring still requires a bound Driver, active calibration, configured/connected camera and confirmed alignment. Registration, cloud relationship changes and remote review need connectivity. Synchronization runs through WorkManager, checks the current authenticated UID and retries on failure. Internet becoming available is necessary but does not guarantee instantaneous completion.

Evidence: [monitoring preconditions](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriverVisionViewModel.kt:513), [background sync conditions](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncWorker.kt:16).

**Correction:** Say that local monitoring works offline after required setup, while eligible uploads occur when network connectivity, authentication, relationship eligibility and background scheduling permit. The recovered text correctly includes approval eligibility; preserve it.

### R12 Lighting recovery is simplified beyond one implemented exception

**Priority: Low. Classification: Methods-level precision, not a core contradiction.**

**Where:** P0105 and P0230, recovery-period wording.

Real poor-light recovery and initial quality establishment use sustained usable observations. However, a new usable sample can restore a recently usable decision after a short gap without restarting the entire recovery period. Current freshness is one second; recent usable restoration permits a gap of up to two seconds.

Evidence: [lighting recovery exception](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/FaceLightingTracker.kt:38).

**Correction:** Use “when the applicable lighting and freshness requirements are restored” in the general scope. Put the precise recent-sample exception in methods if recovery timing is evaluated. The current high-level text is directionally correct; this does not need a large Chapter I explanation.

### R13 Episode merging and interrupted observation need explicit boundaries

**Priority: Medium for reproducible testing. Classification: Operational precision.**

**Where:** P0225, P0234 and P0241.

The manuscript says overlapping signs “may” be associated with one record and a continuous behavior does not repeatedly generate events. The implementation has a deterministic rule: a new sign confirmation is merged into the latest episode while a previously active episode sign remains currently confirmed. When that continuity ends, a subsequent qualifying episode can advance the stage.

Tracking loss, stale results and lighting interruptions can clear evidence. A real-world behavior that continues through an unobserved gap is not guaranteed to retain the same system-observed episode identity. The camera cannot establish what occurred in the gap.

Evidence: [episode continuation and merging](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:265), [invalid evidence reset](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriveAlertDetectionEngine.kt:84).

**Correction:** Add “during uninterrupted valid observation” to the no-repeat claim. Describe merging as the actual episode-continuity rule rather than an arbitrary optional association. Do not introduce a fixed merge timeout that the code does not contain.

### R14 Head nodding needs a precise operational and recovery definition

**Priority: Low to Medium. Classification: Methods precision and construct naming.**

**Where:** P0221–P0225; P0301 already uses the more accurate phrase “downward head posture.”

The implementation detects sustained downward posture, not repeated oscillating nod cycles. It uses relative downward pitch strictly greater than 15 degrees and at least 80% downward valid observations in a mature three-second window. Recovery after confirmation requires a current non-downward observation and a rolling downward ratio below 80%; a single brief upward frame may not re-arm it.

Evidence: [head-down detector](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/temporal/TemporalDetectors.kt:133).

**Correction:** Explicitly define head nodding as the project's operational label for sustained downward head posture. Preserve the manuscript's correct “valid observations” wording. Do not translate 80% of observations into exactly 2.4 seconds continuously head down, because the code does not weight samples by duration. Put exact re-arm rules in methods.

### R15 Calibration time needs valid-interval accounting if methods make timing claims

**Priority: Low. Classification: Clarification rather than a detected false statement.**

**Where:** P0218 says each phase requires five seconds of valid measurements; P0105 excludes rejected lighting frames.

These statements are correct. The implementation accumulates time between adjacent valid samples. An invalid sample breaks continuity but ordinarily preserves valid time already accumulated in that phase. Target changes and explicit navigation/reset have different invalidation behavior. Each phase also waits for acknowledgement and preparation; the whole interaction is longer than four times five seconds.

Evidence: [valid-time accounting](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/calibration/CalibrationSession.kt:66), [target-change invalidation](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriveAlertDetectionEngine.kt:208).

**Correction if expanded:** “Each phase accumulates at least five seconds across valid adjacent observation intervals. Invalid intervals contribute no time; certain target changes and explicit resets invalidate unfinished calibration.” Do not add a requirement for five uninterrupted seconds unless the implementation is changed.

### R16 Evaluated interface, notification and log scope should be named

**Priority: Medium if these functions are included in the evaluated build. Classification: Scope omission.**

**Where:** Dashboard objective P0099; significance P0114–P0115; Flow P0150; Chapter II record-review discussion P0256–P0257.

The source contains related but distinct features:

| Feature | Current boundary | What the manuscript should clarify |
|---|---|---|
| Android Driver dashboard and Alert History | Local Driver monitoring/records | These are the full local individual alert records |
| Trusted Contact Android views and React portal | Eligible shared Stage 3 records | These do not expose full Driver Alert History or live camera control |
| FCM and Cloudflare notification path | Separate dispatch after synchronization | Record upload is not equivalent to notification receipt; deployment and permission state require verification |
| Monitoring Logs | Local account-scoped diagnostic timeline with sign measurements and warning-request results | These are separate from ordinary Alert History and are not shared to the website/Trusted Contacts |

Evidence: [web scope](C:/DriveAlert/drivealert-web/README.md:1), [notification worker requirements](C:/DriveAlert/notification-worker/README.md:1), [Android notification receiver](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/notification/DriveAlertMessagingService.kt:25), [Monitoring Logs](C:/DriveAlert/MONITORING_LOGS.md:1).

**Correction:** Name which interfaces and optional paths are included in the study's evaluated build. This does not require explaining every screen in Chapter I. A concise scope statement is enough. If notifications or logs are excluded, state that explicitly rather than implying that source presence proves deployment or evaluation.

### R17 Comparative claims require stronger evidence labels and product/version scope

**Priority: Medium. Classification: Evidence quality and currentness.**

**Where:** Related Studies P0262–P0268; comparative tables; P0305.

The revised external-camera row is clearer. However, a binary cross for stored records or Trusted Contact synchronization still needs a precise definition and evidence. A page not mentioning a feature does not prove the feature is absent. A fleet dashboard is also not automatically the same feature as DriveAlert's approved personal-contact sharing.

The table says WingDriver has a free mobile application. Its current official site promotes per-active-driver subscription pricing with dashboards, analytics and sharing controls. That does not independently establish a free individual tier. The current Fatigue Alert listing also mentions session statistics; that alone does not prove durable alert-history storage, but it shows why “records” must be defined. Sources: [WingDriver](https://wingdriver.com/), [Fatigue Alert](https://apps.apple.com/us/app/fatigue-alert-stay-awake/id6479893638).

**Correction:** Mark “not documented in the reviewed sources” where absence was not established. Specify product tier, version, platform and retrieval date. The bibliography's July 2026 retrieval dates must not be used as evidence for what a page says in November; preserve historical claims only with corresponding evidence. Avoid presenting an integration contribution as proof that no other system combines similar functions.

The Anti Sleep Pilot table also calls its output a “drowsiness level” while P0265 calls it “fatigue level.” Keep the vendor construct consistent and do not equate it with DriveAlert's three intervention stages.

### R18 Evaluation claims, summary periods and remaining editorial details need precision

**Priority: Medium for evaluation; Low for editorial details. Classification: Research/reporting clarification.**

**Where:** Survey objective P0095; controlled-test scope P0103/P0111; planned threshold tests P0226; descriptive-summary definition P0155; NHTSA citation P0213; source list P1620.

The retrospective survey and controlled technical trial can coexist, but they address different questions. Scripted eye closure or mouth opening validates recognition of a demonstrated sign, not physiological drowsiness. Survey answers do not validate the numerical thresholds. The manuscript correctly excludes clinical confirmation and accident-reduction measurement; preserve those limits.

For reproducible evaluation, distinguish false alerts, missed demonstrated signs, usable assessment time, unavailable assessment time, sign-confirmation latency, command acceptance and actual speaker playback. Do not count a paused interval as a successful negative detection. Android's Delivered state follows an accepted HTTP response, not acoustic confirmation. Evidence: [acceptance check](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/device/HttpWarningOutputGateway.kt:58).

The summary periods also merit definition. The current weekly count is today plus the previous six local calendar days; trend compares that with the preceding seven-day window. Most frequent sign and history highest stage use all retained alerts supplied to the calculator, not necessarily only the weekly subset. Evidence: [summary calculations](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/postauth/AlertHistoryInsights.kt:10). State the periods if these figures will be assessed or presented as results.

Remaining editorial/source-fit items:

- P0150 still has “reachedf”; remove the final `f` and clarify the metric as in R06.
- P0159 Drowsiness Alert definition still lacks its ending period.
- P0213 uses NHTSA 2023, while the relevant webpage entry P1620 is undated. Cite an exact dated report or use the undated webpage consistently.
- P0079's ESP-AT reference points to an ESP32-C3 example, while the production hardware path is classic ESP32-CAM/Arduino firmware. It is broad background support, not the exact implementation reference. Methods should use appropriate classic ESP32/esp32-camera documentation.
- P0257 uses Moens predictive-maintenance dashboard work and Murphy patient-safety dashboard work. These can support cross-domain record visualization principles, but they do not validate driver-drowsiness accuracy or road outcomes. Name the transferred principle.
- The proposal/future-test framing can remain if this is a proposal-stage manuscript. Identify the implementation/evaluation status and do not turn planned trials into completed results by merely changing tense.

## Calibration and detection claims checked against source

| Item | Recovered manuscript | Current implementation | Verdict |
|---|---|---|---|
| Capture/processing split | External OV3660 capture, Android local processing | MJPEG receiver and Android MediaPipe pipeline | Consistent in stated architecture; physical sensor provenance not independently tested here |
| Calibration phases | Neutral, eyes closed, mouth open, head down | Same four phases | Consistent |
| Valid measurement time | Five seconds each | At least 5,000 ms accumulated valid intervals | Consistent with R15 precision |
| EAR threshold | Neutral open-eye average times 0.75 | Same formula | Exact match |
| Closed-eye EAR role | Reference only | Stored; not used in threshold formula | Consistent |
| MAR threshold | Open-mouth average times 0.50 | Same formula | Exact match |
| Neutral MAR | Recorded baseline | Recorded, not the operational MAR threshold source | Consistent |
| Neutral head pitch | Calibrated | Median of neutral pitch samples | Consistent; specify median if giving an exact formula |
| Downward direction | Derived from head-down phase | Sign of median relative head-down delta | Consistent |
| Head angular threshold | Fixed 15-degree relative deviation | Strictly greater than 15 degrees | Consistent |
| Eye confirmation | EAR below threshold for at least 2 seconds | Same comparison and duration | Exact match |
| Eye reset | Reaches or exceeds threshold | EAR greater than or equal | Now exact |
| Yawn confirmation | MAR above threshold for at least 3 seconds | Same comparison and duration | Exact match |
| Yawn reset | Reaches or falls below threshold | MAR less than or equal | Now exact |
| Head persistence | At least 80% of valid observations in rolling 3 seconds | Same ratio; mature window and minimum sample checks | Consistent with R14 precision |
| Signs need not coincide | Independent sign conditions | Independent detectors | Consistent |
| One unavailable sign | Two remaining reliable channels can continue | Minimum two channels | Consistent |
| Fewer than two reliable signs | Assessment pauses | Measurements suppressed, evidence cleared | Consistent |
| Poor lighting | Assessment/calibration evidence gated | Explicit freshness/quality gate | Consistent with R12 precision |
| No identity recognition | Explicit exclusion | Spatial association only | Consistent |

Evidence: [calibration](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/calibration/CalibrationSession.kt:103), [measurement formulas](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/measurement/FacialMeasurementCalculator.kt:9), [temporal detectors](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/temporal/TemporalDetectors.kt:8), [channel availability gate](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/detection/DriveAlertDetectionEngine.kt:108).

## Warning-stage and sharing mechanics checked against source

| Condition | Implemented outcome | Consistency with recovered Chapters 1 and 2 |
|---|---|---|
| First distinct episode with no active stage | Stage 1; start 60-second period | Consistent |
| New distinct episode during Stage 1 | Immediate Stage 2; new period | Consistent |
| New distinct episode during Stage 2 | Immediate Stage 3; transition record | Consistent |
| Persistent confirmed activity at Stage 1 boundary | Stage 2 | Consistent |
| No qualifying activity at Stage 1 boundary | Normal | Consistent |
| Persistent confirmed activity at Stage 2 boundary | Stage 3; transition record | Consistent |
| No qualifying activity at Stage 2 boundary | Stage 1; new period | Consistent |
| New distinct episode during Stage 3 | Stage 3 warning; recurrence recorded; existing period retained | Consistent |
| Stage 3 boundary with recurrence or persistent signs | Stage 3 maintained; one persistence record; next period | Consistent |
| Stage 3 boundary with neither | Stage 2; no persistence record for the completed period | Consistent |
| Physical Stage 1/2 sound | Same selected normal track | Consistent |
| Physical Stage 3 sound | Selected mixed track with rest advisory | Consistent in firmware mapping; actual audio remains a hardware check |
| Ordinary individual alert | Local history record | Consistent in corrected scope and definitions |
| Stage 3 boundary record | Separate local record, then eligibility/upload processing | Consistent |
| Contact approval cutoff | Approved at or before record period start | Consistent |
| Background upload | Network/authentication/eligibility checks and retries | Consistent with operational qualifications |

Evidence: [stage controller](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:103), [physical track mapping](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/warning_output.h:93), [sharing cutoff](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncRemoteDataSource.kt:31), [background worker](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/data/sync/StageSyncWorker.kt:16).

## Important edge cases for methods and acceptance tests

These are not additional contradictions in Chapters 1 and 2. They matter if the next chapter specifies exact data semantics or evaluation cases.

**Transition-period start:** A Stage 3 transition record is built from the outgoing Stage 2 period. Its stored period start can precede the instant Stage 3 is entered. A contact approved midway through that outgoing period can be ineligible for the transition record even if approved before the Stage 3 warning. P0174's period-start rule is correct; the methods should identify what period a transition summarizes. Evidence: [transition construction](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:115).

**Stage 3 re-entry:** Progressing back from Stage 2 to Stage 3 creates another transition record. “Initially enters” should mean an entry into a Stage 3 episode, not necessarily only the first such entry in the entire session.

**Boundary eventCount:** A continued-persistence period can be seeded with one count without a new alert row. A boundary count is therefore not universally the number of newly stored distinct alerts in that minute. Avoid labeling it that way without changing the data contract.

**Pauses and timers:** Losing assessment evidence clears active signs and reminders but does not necessarily freeze the warning-stage timer. The monitoring ticker continues. Recorded recurrence earlier in a Stage 3 period can still satisfy its recurrence condition at the boundary. Do not say that every assessment pause immediately clears stages or freezes every timer.

**Different record stores:** Sign confirmations, grouped Alert History episodes, actual warning requests, Stage 3 synchronization records and notification dispatches have different counts. Monitoring Logs can contain sign/request evidence without becoming ordinary Alert History or externally shared records.

**Minimum warning volume:** The firmware enforces logical levels 26, 28 and 30 on the DFPlayer. This is a software minimum setting, not a measured minimum decibel level or guarantee that a sound was heard. Evidence: [volume mapping](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/warning_output.h:71).

## Primary-source checks refreshed for the recovered audit

| Source | Check result | Implication |
|---|---|---|
| [Ersoy et al. 2026](https://arxiv.org/html/2604.22479v1) | Experimental setup gives 75% of neutral open-eye EAR | Supports the EAR factor; missing bibliography entry remains; identify it as an arXiv preprint |
| [Dong et al. 2026](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0346055) | Gives approximately two/three-second separation and strict greater-than triggers | Supports the duration rationale; DriveAlert's greater-than-or-equal boundary is an adaptation, not an exact replication |
| [Quiles-Cucarella et al. 2024](https://pmc.ncbi.nlm.nih.gov/articles/PMC11398282/) | Full-text extract confirms five-second maximum-mouth initialization, MAR threshold at half the average, and three-second mouth criterion | The manuscript's MAR-factor and yawn-duration attribution is supported; the earlier access-related uncertainty is resolved |
| [Gaspar et al. 2017](https://rosap.ntl.bts.gov/view/dot/62932/dot_62932_DS1.pdf) | Primary article confirms 60-second countermeasure-state mechanics | Supports staged-state rationale; does not directly prescribe DriveAlert's separate synchronization-record frequency |
| [WingDriver official site](https://wingdriver.com/) | Current subscription, dashboard and sharing descriptions differ from a simple free-app comparison | Define version/tier and retain historical claims only with dated evidence |
| [Fatigue Alert official listing](https://apps.apple.com/us/app/fatigue-alert-stay-awake/id6479893638) | Current listing includes session-statistics release information | Define durable alert history separately from session statistics |

The direct Kholerdi author-hosted PDF request timed out in this renewed pass. Its rule was supported in the earlier successful access, but this report does not represent that as a fresh successful fetch. Exact primary full-text verification of Schwarz, Xu, both Jia works, Chandana and Sangeetha, Awasekar, Fitzharris 2017 and Reyes remains incomplete. The missing-entry finding is independently established from the recovered bibliography. This audit does not certify all background statistics, every DOI, or all competitor feature absences.

## Repository documentation cautions

Production code remains the authority for this comparison. `PRODUCT.md` still describes an older prototype phase. `app/DRIVEALERT_DETECTION.md` includes older one-face and visibility-timing descriptions, while the current processor defaults to four candidates and the current visibility controller uses three-second notification and two-second recovery defaults. The recovered manuscript's one-face wording should not be justified solely from that older document.

The command model also still attaches advisory text to Stage 2, but the HTTP payload does not transmit that text and physical firmware uses the ordinary Stage 2 track. This is internal model drift, not evidence that the manuscript is wrong to reserve the physical rest advisory for Stage 3. Evidence: [command model](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/monitoring/WarningStageController.kt:326), [wire payload](C:/DriveAlert/app/src/main/java/com/lhordkent/drivealert/device/HttpWarningOutputGateway.kt:82), [firmware mapping](C:/DriveAlert/drivealert-iot/DriveAlert_Camera_Test/warning_output.h:93).

Source-level relationship rules do not prove that the reviewed rules are deployed to Firestore. No claim of production-secure revocation or live authorization is certified by this audit. The recovered definitions accurately describe the intended checked-in authorization contract.

## Recommended revision sequence

1. Replace the ambiguous one-face description at P0106 and restore the distinction between multi-candidate detection, one selected Driver, and guided measurement calibration.
2. Repair the 12 missing author-year bibliography entries.
3. Add the reminder pathway to Warning Trigger and the intervention discussion.
4. Update the general objective, dashboard objective and Figure 1 sharing bullet to match the already-correct separate Stage 3 record descriptions.
5. Replace the final infrared-supported phrase at P0305 and the stale highest-stage wording/typo at P0150.
6. Add brief scope qualifications for retention, movement guidance, model roles, networking and offline prerequisites.
7. Name the evaluated app/firmware/web build and any excluded newer features; refine methods using the edge cases above.
8. Resolve comparison-evidence labels and the remaining citation/editorial issues, then perform a separate rendered-layout and pagination check.

**Current conclusion:** The recovered Chapters 1 and 2 describe the core detection, calibration, escalation and sharing design accurately in most places. The large earlier infrared-definition and highest-stage-definition findings are withdrawn as current defects. The remaining report is based on the recovered file and current source, with unresolved issues and optional methods precision clearly separated. The original Word file and system source are unchanged.

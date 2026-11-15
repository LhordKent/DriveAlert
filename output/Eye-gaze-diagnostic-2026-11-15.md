# Eye gaze diagnostic — 2026-11-15

User tested without mask/glasses, monitoring stopped: ahead/open (normal blinking), down/open, then gentle bilateral closure about4s. Numeric log only; no images.

| Cue window | Samples | Left blink min–max | Right blink min–max | Left look-down min–max | Right look-down min–max |
|---|---:|---:|---:|---:|---:|
| Initial ahead/open, approximate 15:32:53–15:33:03 |17|.122–.237|.065–.264|.580–.805|.586–.808|
| Down cue→reply 15:33:53–15:34:36 |72|.082–.708|.030–.718|.213–.890|.230–.880|
| Ahead between cues 15:34:38–15:34:50 |15|.029–.241|.038–.408|.427–.731|.448–.725|
| Closed cue→reply 15:34:50–15:35:17 |46|.074–.509|.099–.745|.459–.891|.488–.893|

These are cue-to-reply intervals, including display/reading delays and recovery, not exact continuous pose labels. Strong sustained response appears around15:34:12–29 in the down interval and15:35:05–09 in the closure interval, but their exact physical boundaries are inferred, not independently observed. Do not train/tune a cutoff by treating every interval frame as that pose.

Both blink and look-down activations overlap. Look-down activation is already high in the initial user-confirmed ahead/open sample. A simple blink/look-down threshold is not validated as a fix; a look-down veto could suppress actual closure. No detector/calibration settings changed.

All237 normal DriveAlertEyes readings were UNAVAILABLE with null EAR/MAR/pitch. Selected-face expression diagnostics remained available and face lighting mostly clear; the exact assessment gate was not established. This run does not verify active-session alerts or synchronized EAR separation. Earlier15:05 labeled down-gaze EAR reproduction remains separate evidence.

Collector stopped and test complete; no more pose hold requested. Raw numeric evidence: eye-gaze-phases-2026-11-15.log and eye-gaze-phase-summary-2026-11-15.json. No image capture, speaker commands, firmware flash, app install, commit/push/deploy in this test.

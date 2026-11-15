# Eye-alert monitoring review — 2026-11-13

Inspected the phone's persisted Monitoring Logs and matching debug readings.
No app records or detection settings were changed. The temporary full database
snapshot was deleted after selecting monitoring records; the retained event
export contains no account, device, session or calibration identifiers.

| Saved sign time (Manila) | Eye ratio | Saved threshold | Qualifying duration | Output |
| --- | ---: | ---: | ---: | --- |
| 20:56:08.286 | 0.13894 | 0.28381 | 2,008 ms | Stage 1 request accepted |
| 20:57:37.373 | 0.21416 | 0.28381 | 2,376 ms | Stage 1 accepted, repeat accepted about 3 s later |

Both latest sessions completed with no recorded logging gap. Both saved signs
were PROLONGED_EYE_CLOSURE, not yawning or head nodding. Required duration was
2,000 ms. Camera acceptance does not prove audible playback.

The first candidate ran from about 20:56:06 to 20:56:08. Its left/right open-eye
probabilities included 0.773/0.758, 0.906/0.676, 0.914/0.773 and 0.848/0.520.
Head pitch remained near neutral. Source ages were about 0.42 s and attribute
age was zero on these logged samples. This sequence does not point to a stale
frame or head-nodding event as the immediate trigger.

The second candidate included open-eye scores of 0.039/0.344, 0.141/0.133,
0.012/0.926 and 0.059/0.305. Eyeglasses classifier scores also fluctuated
(roughly 0.46–0.93 in these samples); this is not proof glasses were worn.
At least one sample therefore disagreed strongly between eyes, but the model
also produced several both-low samples. No event-time image was saved.

Current protection only resets an eye-closure candidate when fresh, available,
unobstructed attributes say BOTH eyes are open with probability >=0.90.
Neither sequence met that condition during the qualifying run, so low geometric
EAR continued to confirm closure. Nearby strong both-open samples do reset
candidates, showing the earlier protection is active but incomplete.

These records establish the software trigger, not the person's true eyelid
state. User context (ahead/downward gaze and glasses) is requested to label the
reported false-positive sequence. Changing thresholds or relying on one noisy
classifier score without labeled evidence could also suppress genuine closure.

Artifacts: eye-false-positive-monitoring.log; eye-monitoring-saved-events.json.

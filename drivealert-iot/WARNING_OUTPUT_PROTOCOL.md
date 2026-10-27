# DriveAlert warning-output protocol v1

Android sends one warning command to the ESP32-CAM control server for each
`MonitoringEffect.ActivateWarning` produced by the existing warning-stage controller.

## Request

```http
POST /drivealert/warning HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=utf-8
Accept: application/json

stage=2&sound=DIGITAL_BEEP&volume=MEDIUM
```

The body contains exactly three fields. Unknown, missing, duplicate, or invalid
fields are rejected.

- `stage`: `1`, `2`, or `3`
- `sound`: `DIGITAL_BEEP`, `ROOSTER_CALL`, `ALARM_CLOCK`, `DIGITAL_BEEP_2`, or `BELL_CHIME`
- `volume`: `MINIMUM`, `MEDIUM`, or `HIGH`

Android never transmits advisory text or physical track numbers. Firmware uses
the stage and sound to choose the physical file.

## Responses

- `200 OK` with `{"status":"accepted"}`: the command was queued for playback.
- `400 Bad Request` with `{"error":"invalid_request"}`: the command was invalid.
- `503 Service Unavailable` with `{"error":"warning_output_unavailable"}`: DFPlayer initialization is incomplete or failed.
- `503 Service Unavailable` with `{"error":"warning_output_busy"}`: a prior command is waiting to be consumed.

Android treats only the exact `200 OK` accepted response as `DELIVERED`. Network
errors, timeouts, other status codes, and malformed response bodies are `FAILED`.
There are no automatic retries, preventing unintentional duplicate playback.

## Visibility speech request

Visibility guidance uses a separate endpoint and never changes warning-stage
state:

```http
POST /drivealert/visibility HTTP/1.1
Content-Type: application/x-www-form-urlencoded; charset=utf-8
Accept: application/json

issue=EYES&volume=MEDIUM
```

The body contains exactly `issue` and `volume`. `issue` is `EYES`, `MOUTH`, `FACE`, or
`LIGHT`; volume uses the same identifiers as warning commands. Android sends the
first request after three seconds of sustained visibility loss and repeats it every 30 seconds while
the condition remains active. The warning clears after two seconds of restored
visibility. `LIGHT` uses a dedicated recording and cannot be acknowledged away.
The endpoint uses the same response contract as
`/drivealert/warning`.

## Firmware mapping

| Application sound | Stage 1/2 normal file | Stage 3 mixed file |
|---|---|---|
| `DIGITAL_BEEP` | `/mp3/0001.mp3` | `/mp3/0006.mp3` |
| `ROOSTER_CALL` | `/mp3/0002.mp3` | `/mp3/0007.mp3` |
| `ALARM_CLOCK` | `/mp3/0003.mp3` | `/mp3/0008.mp3` |
| `DIGITAL_BEEP_2` | `/mp3/0004.mp3` | `/mp3/0009.mp3` |
| `BELL_CHIME` | `/mp3/0005.mp3` | `/mp3/0010.mp3` |

| Visibility intent | File | Spoken message |
|---|---|---|
| `EYES` | `/mp3/0011.mp3` | Eyes blocked. Remove the obstruction. |
| `MOUTH` | `/mp3/0012.mp3` | Mouth blocked. Remove the obstruction. |
| `FACE` | `/mp3/0013.mp3` | Face not visible. Restore camera view. |
| `LIGHT` | `/mp3/0014.mp3` | Insufficient lighting. Restore camera visibility. |

| Logical volume | DFPlayer volume |
|---|---:|
| `MINIMUM` | 26 |
| `MEDIUM` | 28 |
| `HIGH` | 30 |

The final files use `/mp3/NNNN.mp3` and `player.playMp3Folder(trackNumber)`.
Stage 3 tracks 6-10 already contain the warning and spoken rest advisory mixed
together, so firmware plays exactly one file.

Warning commands have queue priority over visibility speech. A pending
visibility command may be replaced by a drowsiness warning; visibility speech
never replaces a pending warning. The four speech MP3 files must be created and
copied to the DFPlayer microSD card before hardware testing.

## Insufficient-lighting protection

Android samples the detected face's bounding region from the inference bitmap at
most five times per second, using roughly 2,500 or fewer pixels. It measures a
10%-trimmed mean luminance L and percentile contrast C (p90 minus p10), on 0–255
scales. These are image-quality heuristics, **not MediaPipe confidence scores**.

- Poor: L < 25, or L < 45 and C < 30. Drowsiness assessment pauses immediately.
- Usable: L >= 55, or L >= 35 and C >= 40. Two continuous seconds establish or
  restore usability; borderline readings interrupt recovery and otherwise retain
  the previous decision.
- Unknown, invalid crops, missing samples, or readings older than one second
  cannot enable assessment. Initial usable readings also require two seconds.
- Gated frames preserve actual face-detected status, produce no drowsiness events,
  reset candidate evidence, and do not advance calibration's valid time. Camera
  streaming, foreground monitoring, saved calibration and alert history continue.
- Speech starts three seconds after the first poor reading and repeats every 30
  seconds. Recovery is checked without speaking; a brief poor episode stays silent.
- A missing face is attributed to lighting only when the separate scene-brightness
  tracker has fresh, confirmed darkness; otherwise `FACE` remains authoritative.
  Scene darkness attribution can take longer than a face-area poor reading.

All lighting thresholds are centralized in `FaceLightingTracker` and require
testing/tuning on the actual ESP32-CAM stream. Dim-light accuracy is not established
by unit tests. Do not lower MediaPipe acceptance thresholds as part of this change.

Flash updated firmware **before** installing the updated APK, and place track 14
at `/mp3/0014.mp3` on the DFPlayer card. Older firmware rejects `LIGHT`; rejected,
busy, unavailable or timed-out audio requests never re-enable assessment. With the
existing one-way UART, accepted HTTP means queued, not proof the audio was heard.

Hardware acceptance: verify track 14 and all volume levels; test gradual dimming,
brief shadows, a shadowed face against a bright background, complete darkness,
restored light, camera reconnect, and monitoring with the phone locked/backgrounded.
Confirm no new drowsiness detections are created while gated and recovery requires
two seconds. Do not enable the flash or change exposure settings automatically.

The current card has no separate voice-only advisory file. Stage 2 retains its
domain advisory request, but the physical output can only play the selected
normal track. This is a known media limitation, not a change to warning-stage
semantics.

The UART configuration, Track 1 playback, and volume 30 were physically verified
before this integration. The complete mappings and Android-to-speaker path still
require manual end-to-end verification.

## Verified serial configuration

- `DFRobotDFPlayerMini`
- `HardwareSerial(1)` / UART1
- ESP32-CAM TX GPIO13 to DFPlayer RX
- no ESP32 RX (`-1`)
- 9600 baud, `SERIAL_8N1`
- ACK disabled and library reset disabled
- `DFPLAYER_DEVICE_SD`

The DFPlayer uses its own microSD card. DriveAlert does not use the ESP32-CAM
microSD interface.

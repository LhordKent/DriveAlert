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

## Firmware mapping

| Application sound | Stage 1/2 normal file | Stage 3 mixed file |
|---|---|---|
| `DIGITAL_BEEP` | `/mp3/0001.mp3` | `/mp3/0006.mp3` |
| `ROOSTER_CALL` | `/mp3/0002.mp3` | `/mp3/0007.mp3` |
| `ALARM_CLOCK` | `/mp3/0003.mp3` | `/mp3/0008.mp3` |
| `DIGITAL_BEEP_2` | `/mp3/0004.mp3` | `/mp3/0009.mp3` |
| `BELL_CHIME` | `/mp3/0005.mp3` | `/mp3/0010.mp3` |

| Logical volume | DFPlayer volume |
|---|---:|
| `MINIMUM` | 22 |
| `MEDIUM` | 26 |
| `HIGH` | 30 |

The final files use `/mp3/NNNN.mp3` and `player.playMp3Folder(trackNumber)`.
Stage 3 tracks 6-10 already contain the warning and spoken rest advisory mixed
together, so firmware plays exactly one file.

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

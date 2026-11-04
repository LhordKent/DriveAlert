# Movement advisory deployment

The movement check is an Android advisory based on facial landmark movement.
It is not proof of life and does not prevent moving-photo or replay-video inputs.

## Install once

1. Copy `mp3/0015.mp3` from this directory to `/mp3/0015.mp3` on the **DFPlayer's**
   microSD card, preserving tracks 1–14. This is not the ESP32-CAM's card slot.
2. Flash the updated `DriveAlert_Camera_Test` sketch for `esp32:esp32:esp32cam`.
   No wiring, camera settings, or provisioning changes are required.
3. Install the updated Android APK. Android continues monitoring if old firmware
   rejects `MOVEMENT`, but the new advisory will report failed delivery.

## Track 15

- Text: “Driver movement not detected. Please check the camera view when safe.”
- Voice: Windows Microsoft Zira Desktop, rate 0, generated offline with
  `System.Speech.Synthesis.SpeechSynthesizer`.
- Format: MP3, 44.1 kHz, mono, 128 kbit/s (FFmpeg libmp3lame).
- MP3 duration: **5.433469387755102 seconds** (MPEG frames, including padding).
- Playback reservation: **5,934 ms** = ceil(5,433.469387755102 ms) + 500 ms.
- SHA-256: `02240f3cd20e970892757e03783074bf6e77bd13640b2c17ad66535c4be8a471`.
- Playback reservation: `MOVEMENT_PLAYBACK_GUARD_MS` in `warning_output.h`.
  The recording duration and reservation are documented in the protocol.
- The one-way UART has no completion feedback. Accepted HTTP means queued,
  not proof that speech was played or heard. Verify audibility on the actual unit.

## Quick hardware check

1. Start a calibrated session with a usable camera view. A stationary face/photo
   should show **No movement detected** at 10 seconds without qualifying movement.
2. At 25 seconds total, verify track 15 plays once if no drowsiness sign, visibility
   issue, or higher-priority request is active. Continued stillness must not repeat it.
3. Move the face: the status should clear. Another inactivity episode may speak once.
4. Close eyes: the existing two-second eye-closure alert must still occur. Movement
   speech waits while a drowsiness sign is confirmed; it never cancels that alert.
5. Disconnect/reconnect or obscure the face: existing visibility handling takes
   precedence. Restoration starts a fresh movement interval.

The 2% displacement, 200 ms confirmation, 10-second display, and 25-second speech
settings are provisional engineering defaults. Unit tests verify their rules;
camera noise tolerance and stationary-photo behavior need the actual hardware.

## Host tests

With a C++17 host compiler, compile and run both tests from the repository root:

```text
c++ -std=c++17 drivealert-iot/tests/session_watchdog_test.cpp -o watchdog-test
c++ -std=c++17 -Idrivealert-iot/tests/stubs drivealert-iot/tests/warning_output_test.cpp drivealert-iot/DriveAlert_Camera_Test/warning_output.cpp -o warning-output-test
```

The warning-output test substitutes UART/player stubs; it does not play physical audio.

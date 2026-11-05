# Calibration completion ping

## Spoken preparation countdown

Supply the owner's **2.5-second** “Three, two, one” recording as `/mp3/0017.mp3` on the same DFPlayer card. Keep `/mp3/0016.mp3` for completion. The countdown plays before each of the four steps after Okay; it does not replace the completion ping.

Android sends an authenticated empty-body `POST /drivealert/calibration-countdown`. Firmware queues track 17 at medium volume, reserves 2,500 ms, and waits up to 750 ms for its main loop to dispatch the UART playback command. Only then does it reply `{"status":"started"}`. Busy, unavailable, replaced or expired commands return HTTP 503. Android requires the started response and device identity headers before running its visible 3–2–1 countdown: 834 + 833 + 833 ms, totaling 2,500 ms. Measurement collection begins afterward when visibility is usable. A failed request returns to the instructions and allows a manual retry; it cannot silently start collection.

Timing is conservative: the app starts its preparation delay after the dispatch acknowledgement. Network latency and DFPlayer startup latency mean the screen and audio are not sample-synchronized. One-way UART still cannot verify physical playback, missing tracks, or recording duration. Keep the supplied countdown at 2.5 seconds and test the actual card/speaker before use.

The owner supplies the recording and copies it to the **DFPlayer** microSD card as `/mp3/0016.mp3`. Preserve tracks 0001–0015. No audio is generated or bundled by this change. Use a short, clearly audible ping, preferably under 1.5 seconds; the player reserves two seconds for this cue. The ESP32 camera's card slot is not the DFPlayer card.

Updated Android and firmware are required. Android sends an authenticated, empty-body `POST /drivealert/calibration-ping` once when each measured phase advances: normal posture, eyes closed, mouth open, and head down. It also pings after the last step. Countdown, pause, cancel, navigation back, and repeated frames do not complete a step. Repeating a step and collecting its measurements again produces a new completion ping.

Firmware queues `playMp3Folder(16)` at medium volume (28), only when the player is idle. Warnings supersede a queued calibration cue; a stale queued cue expires after 500 ms. The endpoint returns HTTP 503 if the player is busy/unavailable, HTTP 401 for an unauthorized caller, and HTTP 200 with `{"status":"accepted"}` after enqueue. Android does not automatically retry an ambiguous response, to avoid duplicate pings. The existing one-way UART cannot confirm a file exists or that audio was physically heard. Missing/wrong SD files can therefore be silent despite HTTP acceptance.

All step instructions explain the ping before collection. Eyes-closed explicitly says to open the eyes on the ping; mouth-open says to relax the mouth; head-down says to raise the head. The next step still requires Okay and a countdown. If the app cannot send the cue, it displays completion and tells the user they can release the pose.

## Hardware check before calibration

1. Copy the file, flash updated firmware without erasing NVS, and install the matching APK.
2. While parked, finish normal-posture collection. Confirm exactly one audible ping and the next instruction.
3. Repeat for eyes closed, mouth open, and head down. Confirm the final step also pings.
4. Interrupt camera visibility mid-step: there must be no early ping; collection resumes and pings only after enough valid measurements.
5. Go back and repeat a step: no ping on navigation; one ping after measuring it again. Cancel: no completion ping.
6. Verify track 16 itself is audible and distinct from warning alarms. Software tests cannot validate the supplied recording or speaker output.
7. Tap Okay for every step: hear track 17 once, see 3–2–1, then see valid time begin. Confirm no measurements start during the preparation countdown. Repeat with a busy/unavailable player: instructions must remain available and collection must not begin.

#include "../DriveAlert_Camera_Test/warning_output.h"
#include <cassert>
#include <cstdio>
#include <initializer_list>

unsigned long testMillis = 0;
uint16_t testPlayedTrack = 0;
uint8_t testVolume = 0;

int main() {
  using namespace WarningOutput;
  VisibilityIssue issue;
  assert(parseVisibilityIssue("MOVEMENT", &issue) && issue == VisibilityIssue::MOVEMENT);
  assert(!parseVisibilityIssue("UNKNOWN", &issue));
  assert(!parseVisibilityIssue(nullptr, &issue));
  assert(!parseVisibilityIssue("MOVEMENT", nullptr));
  assert(trackForVisibilityIssue(VisibilityIssue::MOVEMENT) == 15);
  assert(trackForVisibilityIssue(VisibilityIssue::LIGHT) == 14);
  assert(trackForVisibilityIssue(VisibilityIssue::FACE) == 13);
  assert(trackForVisibilityIssue(VisibilityIssue::MOUTH) == 12);
  assert(trackForVisibilityIssue(VisibilityIssue::EYES) == 11);
  for (const auto volume : {Volume::MINIMUM_LEVEL, Volume::MEDIUM_LEVEL, Volume::HIGH_LEVEL}) {
    assert(isValid(VisibilityCommand{VisibilityIssue::MOVEMENT, volume}));
  }
  assert(!isValid(VisibilityCommand{static_cast<VisibilityIssue>(255), Volume::MEDIUM_LEVEL}));
  assert(!isValid(VisibilityCommand{VisibilityIssue::MOVEMENT, static_cast<Volume>(255)}));
  const VisibilityCommand movement{VisibilityIssue::MOVEMENT, Volume::MEDIUM_LEVEL};
  const VisibilityCommand lighting{VisibilityIssue::LIGHT, Volume::HIGH_LEVEL};
  const Command warning{1, Sound::DIGITAL_BEEP, Volume::HIGH_LEVEL, false};
  const Command reminder{1, Sound::DIGITAL_BEEP, Volume::HIGH_LEVEL, true};
  assert(!enqueue(movement)); // Not ready.
  assert(!enqueueCalibrationPing());
  begin();
  for (auto t : {5000UL, 6000UL, 7000UL, 8000UL}) { testMillis = t; loop(); }
  assert(isAvailable());
  assert(enqueue(movement));
  assert(!enqueue(movement)); // No queue behind pending speech.
  assert(enqueue(reminder)); // Drowsiness reminders also replace pending movement.
  loop();
  assert(testPlayedTrack == 1);
  testMillis += 2000;
  assert(enqueue(movement));
  assert(enqueue(lighting)); // Visibility replaces pending movement.
  loop();
  assert(testPlayedTrack == 14);
  assert(!enqueue(movement)); // Cannot interrupt visibility playback.
  testMillis += 4000;
  assert(enqueue(movement));
  assert(enqueue(warning)); // Drowsiness replaces pending movement.
  loop();
  assert(testPlayedTrack == 1 && testVolume == 30);
  assert(!enqueue(movement)); // Cannot interrupt warning playback.
  testMillis += 2000;
  assert(enqueue(movement));
  testMillis += 500;
  loop(); // Stale advisory is discarded.
  assert(testPlayedTrack == 1);
  assert(enqueue(movement));
  loop();
  assert(testPlayedTrack == 15 && testVolume == 28);
  testMillis += MOVEMENT_PLAYBACK_GUARD_MS - 1;
  assert(!enqueue(reminder));
  testMillis++;
  assert(enqueue(reminder));
  loop();
  assert(testPlayedTrack == 1);
  assert(!enqueueCalibrationPing()); // Never interrupts playing warning.
  testMillis += 2000;
  assert(enqueueCalibrationPing());
  assert(!enqueueCalibrationPing()); // No duplicate behind pending ping.
  loop();
  assert(testPlayedTrack == 16 && testVolume == MEDIUM_VOLUME);
  testMillis += CALIBRATION_PLAYBACK_GUARD_MS;
  assert(enqueueCalibrationPing());
  assert(enqueue(warning)); // Warning supersedes pending setup cue.
  loop();
  assert(testPlayedTrack == 1);
  testMillis += 2000;
  assert(enqueueCalibrationPing());
  testMillis += 500;
  loop(); // A stale queued completion cue must not sound later.
  assert(testPlayedTrack == 1);
  assert(enqueueCalibrationCountdown());
  assert(!calibrationCountdownStarted());
  loop();
  assert(calibrationCountdownStarted());
  assert(testPlayedTrack == 17 && testVolume == MEDIUM_VOLUME);
  assert(!enqueueCalibrationPing());
  assert(!enqueueCalibrationCountdown());
  testMillis += 2499;
  assert(!enqueueCalibrationPing());
  testMillis++;
  assert(enqueueCalibrationCountdown());
  assert(!calibrationCountdownStarted());
  assert(enqueue(warning));
  loop();
  assert(!calibrationCountdownStarted()); // Replaced countdown is never acknowledged.
  assert(testPlayedTrack == 1);
  std::puts("Warning output: priorities, calibration ping and 2500 ms countdown dispatch pass.");
}

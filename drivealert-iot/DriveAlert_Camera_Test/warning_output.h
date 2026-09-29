#ifndef DRIVEALERT_WARNING_OUTPUT_H
#define DRIVEALERT_WARNING_OUTPUT_H

#include <Arduino.h>

namespace WarningOutput {

enum class Sound : uint8_t {
  DIGITAL_BEEP,
  ROOSTER_CALL,
  ALARM_CLOCK,
  DIGITAL_BEEP_2,
  BELL_CHIME,
};

enum class Volume : uint8_t {
  MINIMUM_LEVEL,
  MEDIUM_LEVEL,
  HIGH_LEVEL,
};

enum class VisibilityIssue : uint8_t {
  EYES,
  MOUTH,
  FACE,
};

enum class State : uint8_t {
  STARTING,
  READY,
  UNAVAILABLE,
};

struct Command {
  uint8_t stage;
  Sound sound;
  Volume volume;
};

struct VisibilityCommand {
  VisibilityIssue issue;
  Volume volume;
};

constexpr int8_t DFPLAYER_TX_GPIO = 13;
constexpr int8_t DFPLAYER_RX_GPIO = -1;
constexpr uint32_t DFPLAYER_BAUD = 9600;

constexpr uint16_t DIGITAL_BEEP_NORMAL_TRACK = 1;
constexpr uint16_t ROOSTER_CALL_NORMAL_TRACK = 2;
constexpr uint16_t ALARM_CLOCK_NORMAL_TRACK = 3;
constexpr uint16_t DIGITAL_BEEP_2_NORMAL_TRACK = 4;
constexpr uint16_t BELL_CHIME_NORMAL_TRACK = 5;

constexpr uint16_t DIGITAL_BEEP_STAGE_3_TRACK = 6;
constexpr uint16_t ROOSTER_CALL_STAGE_3_TRACK = 7;
constexpr uint16_t ALARM_CLOCK_STAGE_3_TRACK = 8;
constexpr uint16_t DIGITAL_BEEP_2_STAGE_3_TRACK = 9;
constexpr uint16_t BELL_CHIME_STAGE_3_TRACK = 10;
constexpr uint16_t EYES_BLOCKED_TRACK = 11;
constexpr uint16_t MOUTH_BLOCKED_TRACK = 12;
constexpr uint16_t FACE_NOT_VISIBLE_TRACK = 13;

constexpr uint8_t MINIMUM_VOLUME = 22;
constexpr uint8_t MEDIUM_VOLUME = 26;
constexpr uint8_t HIGH_VOLUME = 30;

constexpr uint16_t normalTrackForSound(Sound sound) {
  return sound == Sound::DIGITAL_BEEP ? DIGITAL_BEEP_NORMAL_TRACK
       : sound == Sound::ROOSTER_CALL ? ROOSTER_CALL_NORMAL_TRACK
       : sound == Sound::ALARM_CLOCK ? ALARM_CLOCK_NORMAL_TRACK
       : sound == Sound::DIGITAL_BEEP_2 ? DIGITAL_BEEP_2_NORMAL_TRACK
       : sound == Sound::BELL_CHIME ? BELL_CHIME_NORMAL_TRACK
       : 0;
}

constexpr uint16_t stage3TrackForSound(Sound sound) {
  return sound == Sound::DIGITAL_BEEP ? DIGITAL_BEEP_STAGE_3_TRACK
       : sound == Sound::ROOSTER_CALL ? ROOSTER_CALL_STAGE_3_TRACK
       : sound == Sound::ALARM_CLOCK ? ALARM_CLOCK_STAGE_3_TRACK
       : sound == Sound::DIGITAL_BEEP_2 ? DIGITAL_BEEP_2_STAGE_3_TRACK
       : sound == Sound::BELL_CHIME ? BELL_CHIME_STAGE_3_TRACK
       : 0;
}

constexpr uint16_t trackForStage(uint8_t stage, Sound sound) {
  return stage == 3 ? stage3TrackForSound(sound) : normalTrackForSound(sound);
}

constexpr uint16_t trackForVisibilityIssue(VisibilityIssue issue) {
  return issue == VisibilityIssue::EYES ? EYES_BLOCKED_TRACK
       : issue == VisibilityIssue::MOUTH ? MOUTH_BLOCKED_TRACK
       : issue == VisibilityIssue::FACE ? FACE_NOT_VISIBLE_TRACK
       : 0;
}

constexpr uint8_t valueForVolume(Volume volume) {
  return volume == Volume::MINIMUM_LEVEL ? MINIMUM_VOLUME
       : volume == Volume::MEDIUM_LEVEL ? MEDIUM_VOLUME
       : volume == Volume::HIGH_LEVEL ? HIGH_VOLUME
       : 0;
}

constexpr bool isValidValues(uint8_t stage, Sound sound, Volume volume) {
  return stage >= 1 && stage <= 3 &&
         normalTrackForSound(sound) != 0 &&
         stage3TrackForSound(sound) != 0 &&
         valueForVolume(volume) != 0;
}

void begin();
void loop();
State state();
bool isAvailable();
bool parseSound(const char *value, Sound *sound);
bool parseVolume(const char *value, Volume *volume);
bool parseVisibilityIssue(const char *value, VisibilityIssue *issue);
bool isValid(const Command &command);
bool isValid(const VisibilityCommand &command);
bool enqueue(const Command &command);
bool enqueue(const VisibilityCommand &command);

}  // namespace WarningOutput

#endif

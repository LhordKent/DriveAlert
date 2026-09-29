#include "warning_output.h"

#include <DFRobotDFPlayerMini.h>

namespace WarningOutput {
namespace {
constexpr unsigned long PLAYER_POWER_UP_MS = 5000;
constexpr unsigned long PLAYER_COMMAND_SETTLE_MS = 1000;

enum class StartupStep : uint8_t {
  WAIT_FOR_PLAYER,
  WAIT_FOR_SD_SELECTION,
  WAIT_FOR_INITIAL_VOLUME,
  WAIT_FOR_READY,
  COMPLETE,
  FAILED,
};

HardwareSerial dfSerial(1);
DFRobotDFPlayerMini player;
StartupStep startupStep = StartupStep::WAIT_FOR_PLAYER;
unsigned long stepStartedAtMs = 0;
portMUX_TYPE commandMux = portMUX_INITIALIZER_UNLOCKED;
struct PlaybackCommand {
  uint16_t track;
  Volume volume;
  bool highPriority;
};
PlaybackCommand pendingCommand = {};
volatile bool commandPending = false;

bool elapsed(unsigned long now, unsigned long startedAt, unsigned long duration) {
  return now - startedAt >= duration;
}

bool takePending(PlaybackCommand *command) {
  bool available = false;
  portENTER_CRITICAL(&commandMux);
  if (commandPending) {
    *command = pendingCommand;
    commandPending = false;
    available = true;
  }
  portEXIT_CRITICAL(&commandMux);
  return available;
}

bool enqueuePlayback(uint16_t track, Volume volume, bool highPriority) {
  if (!isAvailable() || track == 0 || valueForVolume(volume) == 0) return false;
  bool accepted = false;
  portENTER_CRITICAL(&commandMux);
  if (!commandPending || (highPriority && !pendingCommand.highPriority)) {
    pendingCommand = {track, volume, highPriority};
    commandPending = true;
    accepted = true;
  }
  portEXIT_CRITICAL(&commandMux);
  return accepted;
}
}  // namespace

static_assert(normalTrackForSound(Sound::DIGITAL_BEEP) == 1, "Digital Beep normal mapping changed");
static_assert(normalTrackForSound(Sound::ROOSTER_CALL) == 2, "Rooster Call normal mapping changed");
static_assert(normalTrackForSound(Sound::ALARM_CLOCK) == 3, "Alarm Clock normal mapping changed");
static_assert(normalTrackForSound(Sound::DIGITAL_BEEP_2) == 4, "Digital Beep 2 normal mapping changed");
static_assert(normalTrackForSound(Sound::BELL_CHIME) == 5, "Bell Chime normal mapping changed");
static_assert(stage3TrackForSound(Sound::DIGITAL_BEEP) == 6, "Digital Beep Stage 3 mapping changed");
static_assert(stage3TrackForSound(Sound::ROOSTER_CALL) == 7, "Rooster Call Stage 3 mapping changed");
static_assert(stage3TrackForSound(Sound::ALARM_CLOCK) == 8, "Alarm Clock Stage 3 mapping changed");
static_assert(stage3TrackForSound(Sound::DIGITAL_BEEP_2) == 9, "Digital Beep 2 Stage 3 mapping changed");
static_assert(stage3TrackForSound(Sound::BELL_CHIME) == 10, "Bell Chime Stage 3 mapping changed");
static_assert(trackForStage(1, Sound::DIGITAL_BEEP) == 1, "Stage 1 must use the normal track");
static_assert(trackForStage(2, Sound::DIGITAL_BEEP) == 1, "Stage 2 media limitation must use the normal track");
static_assert(trackForStage(3, Sound::DIGITAL_BEEP) == 6, "Stage 3 must use the mixed track");
static_assert(valueForVolume(Volume::MINIMUM_LEVEL) == 22, "Minimum volume mapping changed");
static_assert(valueForVolume(Volume::MEDIUM_LEVEL) == 26, "Medium volume mapping changed");
static_assert(valueForVolume(Volume::HIGH_LEVEL) == 30, "High volume mapping changed");
static_assert(!isValidValues(0, Sound::ROOSTER_CALL, Volume::MINIMUM_LEVEL), "Stage 0 must be invalid");
static_assert(!isValidValues(4, Sound::ROOSTER_CALL, Volume::MINIMUM_LEVEL), "Stage 4 must be invalid");
static_assert(!isValidValues(1, static_cast<Sound>(255), Volume::MINIMUM_LEVEL), "Unknown sound must be invalid");
static_assert(!isValidValues(1, Sound::ROOSTER_CALL, static_cast<Volume>(255)), "Unknown volume must be invalid");
static_assert(isValidValues(1, Sound::ROOSTER_CALL, Volume::MINIMUM_LEVEL), "Stage 1 must be valid");
static_assert(isValidValues(2, Sound::ROOSTER_CALL, Volume::MINIMUM_LEVEL), "Stage 2 must be valid");
static_assert(isValidValues(3, Sound::ROOSTER_CALL, Volume::MINIMUM_LEVEL), "Stage 3 must be valid");
static_assert(trackForVisibilityIssue(VisibilityIssue::EYES) == 11, "Eye obstruction track changed");
static_assert(trackForVisibilityIssue(VisibilityIssue::MOUTH) == 12, "Mouth obstruction track changed");
static_assert(trackForVisibilityIssue(VisibilityIssue::FACE) == 13, "Face visibility track changed");

void begin() {
  // This is the physically verified one-way UART configuration. Initialization
  // continues from loop() so camera and network startup are not blocked by delays.
  dfSerial.begin(DFPLAYER_BAUD, SERIAL_8N1, DFPLAYER_RX_GPIO, DFPLAYER_TX_GPIO);
  startupStep = StartupStep::WAIT_FOR_PLAYER;
  stepStartedAtMs = millis();
}

void loop() {
  const unsigned long now = millis();
  switch (startupStep) {
    case StartupStep::WAIT_FOR_PLAYER:
      if (!elapsed(now, stepStartedAtMs, PLAYER_POWER_UP_MS)) return;
      // ACK and library reset are both disabled for the verified one-way link.
      if (!player.begin(dfSerial, false, false)) {
        startupStep = StartupStep::FAILED;
        Serial.println("DFPlayer initialization failed; warning output is unavailable.");
        return;
      }
      startupStep = StartupStep::WAIT_FOR_SD_SELECTION;
      stepStartedAtMs = now;
      return;
    case StartupStep::WAIT_FOR_SD_SELECTION:
      if (!elapsed(now, stepStartedAtMs, PLAYER_COMMAND_SETTLE_MS)) return;
      player.outputDevice(DFPLAYER_DEVICE_SD);
      startupStep = StartupStep::WAIT_FOR_INITIAL_VOLUME;
      stepStartedAtMs = now;
      return;
    case StartupStep::WAIT_FOR_INITIAL_VOLUME:
      if (!elapsed(now, stepStartedAtMs, PLAYER_COMMAND_SETTLE_MS)) return;
      player.volume(HIGH_VOLUME);
      startupStep = StartupStep::WAIT_FOR_READY;
      stepStartedAtMs = now;
      return;
    case StartupStep::WAIT_FOR_READY:
      if (!elapsed(now, stepStartedAtMs, PLAYER_COMMAND_SETTLE_MS)) return;
      startupStep = StartupStep::COMPLETE;
      Serial.println("DFPlayer warning output ready on UART1 TX GPIO13.");
      break;
    case StartupStep::COMPLETE:
      break;
    case StartupStep::FAILED:
      return;
  }

  PlaybackCommand command;
  if (!takePending(&command)) return;
  player.volume(valueForVolume(command.volume));
  player.playMp3Folder(command.track);
}

State state() {
  if (startupStep == StartupStep::COMPLETE) return State::READY;
  if (startupStep == StartupStep::FAILED) return State::UNAVAILABLE;
  return State::STARTING;
}

bool isAvailable() {
  return state() == State::READY;
}

bool parseSound(const char *value, Sound *sound) {
  if (value == nullptr || sound == nullptr) return false;
  if (!strcmp(value, "DIGITAL_BEEP")) *sound = Sound::DIGITAL_BEEP;
  else if (!strcmp(value, "ROOSTER_CALL")) *sound = Sound::ROOSTER_CALL;
  else if (!strcmp(value, "ALARM_CLOCK")) *sound = Sound::ALARM_CLOCK;
  else if (!strcmp(value, "DIGITAL_BEEP_2")) *sound = Sound::DIGITAL_BEEP_2;
  else if (!strcmp(value, "BELL_CHIME")) *sound = Sound::BELL_CHIME;
  else return false;
  return true;
}

bool parseVolume(const char *value, Volume *volume) {
  if (value == nullptr || volume == nullptr) return false;
  if (!strcmp(value, "MINIMUM")) *volume = Volume::MINIMUM_LEVEL;
  else if (!strcmp(value, "MEDIUM")) *volume = Volume::MEDIUM_LEVEL;
  else if (!strcmp(value, "HIGH")) *volume = Volume::HIGH_LEVEL;
  else return false;
  return true;
}

bool parseVisibilityIssue(const char *value, VisibilityIssue *issue) {
  if (value == nullptr || issue == nullptr) return false;
  if (!strcmp(value, "EYES")) *issue = VisibilityIssue::EYES;
  else if (!strcmp(value, "MOUTH")) *issue = VisibilityIssue::MOUTH;
  else if (!strcmp(value, "FACE")) *issue = VisibilityIssue::FACE;
  else return false;
  return true;
}

bool isValid(const Command &command) {
  return isValidValues(command.stage, command.sound, command.volume);
}

bool isValid(const VisibilityCommand &command) {
  return trackForVisibilityIssue(command.issue) != 0 && valueForVolume(command.volume) != 0;
}

bool enqueue(const Command &command) {
  if (!isValid(command)) return false;
  return enqueuePlayback(trackForStage(command.stage, command.sound), command.volume, true);
}

bool enqueue(const VisibilityCommand &command) {
  if (!isValid(command)) return false;
  return enqueuePlayback(trackForVisibilityIssue(command.issue), command.volume, false);
}

}  // namespace WarningOutput

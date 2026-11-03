#pragma once
#include "Arduino.h"
#define DFPLAYER_DEVICE_SD 2
extern uint16_t testPlayedTrack;
extern uint8_t testVolume;
struct DFRobotDFPlayerMini {
  bool begin(HardwareSerial &, bool, bool) { return true; }
  void outputDevice(int) {}
  void volume(uint8_t volume) { testVolume = volume; }
  void playMp3Folder(uint16_t track) { testPlayedTrack = track; }
};

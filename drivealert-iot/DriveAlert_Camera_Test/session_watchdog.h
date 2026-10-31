#pragma once

#include <stdint.h>

// Caller synchronizes access between the Arduino loop and HTTP tasks.
// Unsigned subtraction intentionally handles millis() wraparound.
class SessionWatchdog {
 public:
  static constexpr uint32_t GRACE_MS = 180000;
  constexpr void begin(uint32_t now) { lastActivity = now; armed = true; }
  constexpr void touch(uint32_t now) { if (armed) lastActivity = now; }
  constexpr bool expired(uint32_t now) const {
    return armed && static_cast<uint32_t>(now - lastActivity) >= GRACE_MS;
  }

 private:
  uint32_t lastActivity = 0;
  bool armed = false;
};

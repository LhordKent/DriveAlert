#ifndef DRIVEALERT_RESET_BUTTON_DEBOUNCER_H
#define DRIVEALERT_RESET_BUTTON_DEBOUNCER_H

#include <stdint.h>

namespace ResetButton {
constexpr uint32_t DEBOUNCE_MS = 50;

// Pure active-low button state, kept independent of Arduino for deterministic tests.
class Debouncer {
 public:
  constexpr void begin(uint32_t nowMs, bool pressed) {
    lastPressed = pressed;
    changedAtMs = nowMs;
    armed = false;
    triggered = false;
  }

  constexpr bool update(uint32_t nowMs, bool pressed) {
    if (triggered) return false;
    if (pressed != lastPressed) {
      lastPressed = pressed;
      changedAtMs = nowMs;
      return false;
    }
    // Unsigned subtraction remains valid when millis() wraps around.
    if (static_cast<uint32_t>(nowMs - changedAtMs) < DEBOUNCE_MS) return false;
    if (!pressed) {
      // Every boot requires a stable release, including when held during restart.
      armed = true;
      return false;
    }
    if (!armed) return false;
    triggered = true;  // Latch before restarting: at most one attempt this boot.
    return true;
  }

 private:
  uint32_t changedAtMs = 0;
  bool lastPressed = false;
  bool armed = false;
  bool triggered = false;
};
}  // namespace ResetButton

#endif

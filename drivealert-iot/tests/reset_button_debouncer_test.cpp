#include "../DriveAlert_Camera_Test/reset_button_debouncer.h"

namespace {
constexpr bool releasedButtonDoesNotRestart() {
  ResetButton::Debouncer button;
  button.begin(0, false);
  return !button.update(50, false) && !button.update(5000, false);
}

constexpr bool pressRequiresStableLow() {
  ResetButton::Debouncer button;
  button.begin(0, false);
  return !button.update(50, false) && !button.update(100, true) &&
         !button.update(149, true) && button.update(150, true);
}

constexpr bool noiseAndContactBounceDoNotRestart() {
  ResetButton::Debouncer button;
  button.begin(0, false);
  return !button.update(50, false) && !button.update(100, true) &&
         !button.update(120, false) && !button.update(130, true) &&
         !button.update(160, false) && !button.update(170, true) &&
         !button.update(219, true) && button.update(220, true);
}

constexpr bool oneRestartAttemptPerBoot() {
  ResetButton::Debouncer button;
  button.begin(0, false);
  return !button.update(50, false) && !button.update(100, true) &&
         button.update(150, true) && !button.update(5000, true) &&
         !button.update(5100, false) && !button.update(5200, false) &&
         !button.update(5300, true) && !button.update(5400, true);
}

constexpr bool heldButtonAcrossRebootRequiresRelease() {
  ResetButton::Debouncer button;
  button.begin(0, true);
  return !button.update(50, true) && !button.update(5000, true) &&
         !button.update(5100, false) && !button.update(5149, false) &&
         !button.update(5150, false) && !button.update(5200, true) &&
         button.update(5250, true);
}

constexpr bool releaseBounceCannotArmButton() {
  ResetButton::Debouncer button;
  button.begin(0, true);
  return !button.update(100, false) && !button.update(140, true) &&
         !button.update(500, true) && !button.update(600, false) &&
         !button.update(650, false) && !button.update(700, true) &&
         button.update(750, true);
}

constexpr bool millisWrapDoesNotChangeDebounceTime() {
  ResetButton::Debouncer button;
  button.begin(UINT32_MAX - 100, false);
  return !button.update(UINT32_MAX - 50, false) && !button.update(UINT32_MAX - 20, true) &&
         !button.update(28, true) && button.update(29, true);
}

static_assert(releasedButtonDoesNotRestart(), "Released button must not restart");
static_assert(pressRequiresStableLow(), "Press must remain LOW for 50 ms");
static_assert(noiseAndContactBounceDoNotRestart(), "Bounce must restart the debounce interval");
static_assert(oneRestartAttemptPerBoot(), "Restart must be latched before invoking the handler");
static_assert(heldButtonAcrossRebootRequiresRelease(), "Held button must not cause a reboot loop");
static_assert(releaseBounceCannotArmButton(), "Boot arming must require a stable release");
static_assert(millisWrapDoesNotChangeDebounceTime(), "Debounce must survive millis rollover");
}  // namespace

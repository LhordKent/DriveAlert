#include "../DriveAlert_Camera_Test/session_watchdog.h"
#include <assert.h>
#include <stdio.h>

constexpr bool watchdogContract() {
  SessionWatchdog timer;
  if (timer.expired(999999)) return false;
  timer.begin(1000);
  if (timer.expired(180999) || !timer.expired(181000)) return false;
  timer.touch(180900);
  if (timer.expired(181000) || timer.expired(360899) || !timer.expired(360900)) return false;
  timer.begin(UINT32_MAX - 1000);
  if (timer.expired(500) || !timer.expired(179000)) return false;
  timer.touch(179000);
  return !timer.expired(179001);
}
static_assert(watchdogContract(), "Session grace, renewals, expiry and millis wraparound must hold");

int main() {
  SessionWatchdog timer;
  assert(!timer.expired(999999));
  timer.begin(1000);
  assert(!timer.expired(180999));
  assert(timer.expired(181000));
  timer.touch(180900); // heartbeat or successfully delivered stream frame
  assert(!timer.expired(181000));
  assert(!timer.expired(360899));
  assert(timer.expired(360900));
  timer.begin(UINT32_MAX - 1000);
  assert(!timer.expired(500));
  assert(timer.expired(179000));
  timer.touch(179000);
  assert(!timer.expired(179001));
  puts("Session watchdog: grace, renewals, expiry and millis wraparound pass.");
}

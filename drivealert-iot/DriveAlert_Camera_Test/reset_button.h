#ifndef DRIVEALERT_RESET_BUTTON_H
#define DRIVEALERT_RESET_BUTTON_H

#include <stdint.h>

namespace ResetButton {
constexpr uint8_t BUTTON_GPIO = 15;
using RestartHandler = void (*)();

// Start a yielding polling task so reset also works during blocking Wi-Fi setup.
void begin(RestartHandler restartHandler);
}  // namespace ResetButton

#endif

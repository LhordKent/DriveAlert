#include "reset_button.h"

#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>

#include "board_config.h"
#include "reset_button_debouncer.h"

namespace ResetButton {
namespace {
constexpr uint32_t POLL_INTERVAL_MS = 10;
constexpr uint32_t TASK_STACK_BYTES = 2048;
TaskHandle_t buttonTaskHandle = nullptr;
RestartHandler restart = nullptr;

constexpr bool isCameraPin(int gpio) {
  return gpio == PWDN_GPIO_NUM || gpio == RESET_GPIO_NUM || gpio == XCLK_GPIO_NUM ||
         gpio == SIOD_GPIO_NUM || gpio == SIOC_GPIO_NUM || gpio == Y9_GPIO_NUM ||
         gpio == Y8_GPIO_NUM || gpio == Y7_GPIO_NUM || gpio == Y6_GPIO_NUM ||
         gpio == Y5_GPIO_NUM || gpio == Y4_GPIO_NUM || gpio == Y3_GPIO_NUM ||
         gpio == Y2_GPIO_NUM || gpio == VSYNC_GPIO_NUM || gpio == HREF_GPIO_NUM ||
         gpio == PCLK_GPIO_NUM;
}

static_assert(!isCameraPin(BUTTON_GPIO), "Reset button conflicts with the selected camera GPIO map");
#if defined(LED_GPIO_NUM)
static_assert(BUTTON_GPIO != LED_GPIO_NUM, "Reset button conflicts with the camera flash LED");
#endif
static_assert(pdMS_TO_TICKS(POLL_INTERVAL_MS) > 0, "Reset polling must yield at least one scheduler tick");

void pollButton(void *) {
  Debouncer button;
  button.begin(millis(), digitalRead(BUTTON_GPIO) == LOW);
  for (;;) {
    // INPUT_PULLUP makes release HIGH; a press connects GPIO15 to GND (LOW).
    // The debouncer requires 50 ms of stable release to arm, then 50 ms LOW.
    // It latches before invoking the handler, even if restart unexpectedly returns.
    if (button.update(millis(), digitalRead(BUTTON_GPIO) == LOW)) restart();
    vTaskDelay(pdMS_TO_TICKS(POLL_INTERVAL_MS));
  }
}
}  // namespace

void begin(RestartHandler restartHandler) {
  if (buttonTaskHandle != nullptr || restartHandler == nullptr) return;
  // GPIO15 is SD CMD/MTDO on classic ESP32. The AI-Thinker camera does not use it,
  // and DriveAlert does not initialize the ESP32-CAM microSD/SDIO interface.
  // A held button can silence ROM boot messages; this is not the EN hardware reset.
  pinMode(BUTTON_GPIO, INPUT_PULLUP);
  restart = restartHandler;
  if (xTaskCreate(pollButton, "reset_button", TASK_STACK_BYTES, nullptr, 1, &buttonTaskHandle) != pdPASS) {
    buttonTaskHandle = nullptr;
    Serial.println("Reset button task could not start; GPIO15 software reset is unavailable.");
    return;
  }
  Serial.println("Reset button ready on GPIO15 (active LOW, 50 ms debounce; release to arm).");
}
}  // namespace ResetButton

#pragma once
#include <cstdint>
#include <cstring>

extern unsigned long testMillis;
inline unsigned long millis() { return testMillis; }
using portMUX_TYPE = int;
#define portMUX_INITIALIZER_UNLOCKED 0
#define portENTER_CRITICAL(mux) ((void)(mux))
#define portEXIT_CRITICAL(mux) ((void)(mux))
#define SERIAL_8N1 0
struct HardwareSerial {
  explicit HardwareSerial(int) {}
  void begin(uint32_t, int, int8_t, int8_t) {}
  void println(const char *) {}
};
inline HardwareSerial Serial(0);

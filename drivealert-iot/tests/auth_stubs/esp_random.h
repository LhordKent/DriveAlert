#pragma once
#include <cstdint>
inline uint8_t testRandomByte = 0x10;
inline void esp_fill_random(void *target, size_t length) { memset(target, testRandomByte++, length); }

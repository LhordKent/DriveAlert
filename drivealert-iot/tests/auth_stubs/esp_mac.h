#pragma once
#include <cstdint>
#define ESP_MAC_BT 2
inline int esp_read_mac(uint8_t *target, int) { const uint8_t address[] = {0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF}; memcpy(target, address, 6); return 0; }

#pragma once
#include "Arduino.h"
#include <map>
#include <vector>
inline std::map<std::string, std::vector<uint8_t>> testPreferences;
inline bool testStorageFailure = false;
class Preferences {
 public:
  bool begin(const char *, bool = false) { return !testStorageFailure; }
  void end() {}
  uint32_t getUInt(const char *key, uint32_t fallback) { auto it = testPreferences.find(key); uint32_t result = fallback; if (it != testPreferences.end() && it->second.size() == sizeof(result)) memcpy(&result, it->second.data(), sizeof(result)); return result; }
  size_t putUInt(const char *key, uint32_t value) { return putBytes(key, &value, sizeof(value)); }
  String getString(const char *key, const char *fallback) { auto it = testPreferences.find(key); return it == testPreferences.end() ? String(fallback) : String(std::string(it->second.begin(), it->second.end())); }
  size_t putString(const char *key, const String &value) { if (testStorageFailure) return 0; testPreferences[key] = std::vector<uint8_t>(value.begin(), value.end()); return value.size(); }
  size_t getBytesLength(const char *key) { return testPreferences[key].size(); }
  size_t getBytes(const char *key, void *target, size_t length) { auto &value = testPreferences[key]; auto copied = std::min(length, value.size()); memcpy(target, value.data(), copied); return copied; }
  size_t putBytes(const char *key, const void *source, size_t length) { if (testStorageFailure) return 0; auto bytes = static_cast<const uint8_t *>(source); testPreferences[key] = std::vector<uint8_t>(bytes, bytes + length); return length; }
};

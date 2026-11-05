#pragma once
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <string>
#include <type_traits>
using std::max;
#define RTC_DATA_ATTR
#define RTC_NOINIT_ATTR __attribute__((section(".test_rtc_noinit")))
extern unsigned long testMillis;
inline unsigned long millis() { return testMillis; }
class String : public std::string {
 public:
  using std::string::string;
  String() = default;
  String(const std::string &value) : std::string(value) {}
  template <class T, typename std::enable_if<std::is_integral<T>::value, int>::type = 0>
  explicit String(T value) : std::string(std::to_string(value)) {}
  bool isEmpty() const { return empty(); }
  bool startsWith(const char *prefix) const { return rfind(prefix, 0) == 0; }
  char operator[](size_t index) const { return index < size() ? at(index) : 0; }
  String substring(size_t start) const { return start < size() ? substr(start) : ""; }
  String substring(size_t start, size_t end) const { return start < size() && end >= start ? substr(start, end - start) : ""; }
  int indexOf(char needle, size_t start = 0) const { auto result = find(needle, start); return result == npos ? -1 : static_cast<int>(result); }
  void toCharArray(char *target, size_t capacity) const { if (capacity) { auto length = std::min(size(), capacity - 1); memcpy(target, data(), length); target[length] = 0; } }
};
inline String operator+(const String &a, const String &b) { return String(static_cast<const std::string &>(a) + static_cast<const std::string &>(b)); }
inline String operator+(const char *a, const String &b) { return String(a) + b; }
inline String operator+(const String &a, const char *b) { return a + String(b); }

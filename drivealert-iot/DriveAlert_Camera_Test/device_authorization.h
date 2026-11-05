#pragma once
#include <Arduino.h>
#include "esp_http_server.h"

namespace DeviceAuthorization {
void begin(bool restoreHandoff);
void newBleConnection();
void endBleConnection();
String deviceId();
uint32_t pairingPin();
String command(const String &value);
bool bleAuthorized();
void preserveHandoff();
bool requireHttp(httpd_req_t *req);
}

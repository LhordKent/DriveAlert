#pragma once
#include "Arduino.h"
#define ESP_OK 0
using esp_err_t = int;
struct httpd_req_t { String authorization; String status; String response; };
inline size_t httpd_req_get_hdr_value_len(httpd_req_t *req, const char *) { return req->authorization.length(); }
inline int httpd_req_get_hdr_value_str(httpd_req_t *req, const char *, char *target, size_t length) { req->authorization.toCharArray(target, length); return 0; }
inline void httpd_resp_set_status(httpd_req_t *req, const char *status) { req->status = status; }
inline void httpd_resp_set_type(httpd_req_t *, const char *) {}
inline void httpd_resp_set_hdr(httpd_req_t *, const char *, const char *) {}
inline void httpd_resp_sendstr(httpd_req_t *req, const char *value) { req->response = value; }

#ifndef DRIVEALERT_PROVISIONING_PROTOCOL_H
#define DRIVEALERT_PROVISIONING_PROTOCOL_H

namespace DriveAlertProvisioningProtocol {
constexpr char SERVICE_UUID[] = "7f9c1000-8b6e-4c5a-9d2f-6a3b7c8d0001";
constexpr char SSID_UUID[] = "7f9c1001-8b6e-4c5a-9d2f-6a3b7c8d0001";
constexpr char PASSWORD_UUID[] = "7f9c1002-8b6e-4c5a-9d2f-6a3b7c8d0001";
constexpr char APPLY_UUID[] = "7f9c1003-8b6e-4c5a-9d2f-6a3b7c8d0001";
constexpr char STATUS_UUID[] = "7f9c1004-8b6e-4c5a-9d2f-6a3b7c8d0001";

constexpr char APPLY_COMMAND[] = "APPLY";
constexpr char QUERY_CREDENTIALS_COMMAND[] = "QUERY_CREDENTIALS";
constexpr char CONNECT_SAVED_COMMAND[] = "CONNECT_SAVED";
constexpr char STATUS_READY[] = "READY";
constexpr char STATUS_RECEIVED[] = "RECEIVED";
constexpr char STATUS_NO_CREDENTIALS[] = "CREDENTIALS|NONE";
constexpr char STATUS_SAVED_CREDENTIALS_PREFIX[] = "CREDENTIALS|SAVED|";
constexpr char STATUS_CONNECTING[] = "CONNECTING";
constexpr char STATUS_CONNECTION_FAILED[] = "CONNECTION_FAILED";
constexpr char STATUS_INVALID_CREDENTIALS[] = "INVALID_CREDENTIALS";
constexpr char STATUS_ERROR[] = "ERROR";
}  // namespace DriveAlertProvisioningProtocol

#endif

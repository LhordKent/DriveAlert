# DriveAlert BLE provisioning protocol v1

This is the shared source of truth for the Android app and classic ESP32-CAM firmware.

## Device identity and security

- Advertised name: `DriveAlert-XXXX`
- Service is advertised only while provisioning is required.
- Pairing: LE Secure Connections, MITM authentication, bonding
- ESP32 I/O capability: Display Only; its development serial console displays the device-specific six-digit PIN
- Credential and command writes require encrypted MITM access.
- Status reads/notifications require encrypted MITM access.

## UUIDs

| Purpose | UUID | Direction | Encoding |
|---|---|---|---|
| Service | `7f9c1000-8b6e-4c5a-9d2f-6a3b7c8d0001` | — | — |
| SSID | `7f9c1001-8b6e-4c5a-9d2f-6a3b7c8d0001` | Android → ESP32 | UTF-8, 1–32 bytes |
| Password | `7f9c1002-8b6e-4c5a-9d2f-6a3b7c8d0001` | Android → ESP32 | UTF-8, empty or 8–63 bytes |
| Command | `7f9c1003-8b6e-4c5a-9d2f-6a3b7c8d0001` | Android → ESP32 | UTF-8 command |
| Status | `7f9c1004-8b6e-4c5a-9d2f-6a3b7c8d0001` | ESP32 → Android | UTF-8 read/notify |

Android requests an MTU of 128 and sends `QUERY_CREDENTIALS` after subscribing to status notifications. For a new network it writes SSID, password, then `APPLY`. For an existing saved network it sends `CONNECT_SAVED`. Every write waits for the preceding GATT callback.

Neither `QUERY_CREDENTIALS` nor merely connecting over BLE starts Wi-Fi. Only `APPLY` and `CONNECT_SAVED` authorize a Wi-Fi attempt for the current power cycle.

## Status values

- `READY`: service initialized
- `RECEIVED`: an input field was accepted into volatile candidate memory
- `CREDENTIALS|NONE`: no structurally valid saved credentials exist
- `CREDENTIALS|SAVED|<SSID>`: credentials exist; only the SSID is returned and its bytes, including edge whitespace, are preserved
- `CONNECTING`: bounded candidate Wi-Fi attempt started
- `CONNECTED|<IPv4>|<hostname>`: connection succeeded and active credentials were persisted
  (older firmware may omit the hostname; Android accepts both forms)
- `INVALID_CREDENTIALS`: local length/format validation failed
- `CONNECTION_FAILED`: network was not joined within 20 seconds
- `ERROR`: connected credentials could not be persisted

The status channel never returns the password. It returns the saved SSID so Android can offer the network without asking the user to re-enter credentials. The IPv4 value is the current DHCP address and is not a permanent discovery identifier.

After joining Wi-Fi, firmware advertises `_drivealert._tcp` on port 81 with
`path=/stream` and `controlPort=80` TXT metadata. The stable hostname is derived
from the ESP32 identity, for example `drivealert-e9d4.local`.

## Lifecycle

Every boot starts BLE and remains disconnected from Wi-Fi regardless of saved NVS credentials. BLE provisioning is deinitialized only after an explicit command succeeds and its status is delivered. Camera and HTTP servers start afterward. On failure, candidate password memory is cleared and BLE remains available for another attempt.

## Explicit session disconnect

While normal camera mode is active, Android may send `POST /drivealert/disconnect`
to the numeric device IP on control port 80. The ESP32 replies with
`DISCONNECTING`, disables reconnect for the current session, and restarts into
BLE-ready mode. Saved SSID and password values remain in NVS and are not used
again until Android sends an explicit connect command.

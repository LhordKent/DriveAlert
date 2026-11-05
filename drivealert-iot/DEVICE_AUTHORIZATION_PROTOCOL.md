# Offline device authorization, protocol 1

This protocol supplements the existing Wi-Fi provisioning UUIDs and replaces unauthenticated setup/HTTP access. The updated Android app and firmware must be released together. Older apps cannot provision or stream from the updated firmware.

## Identity and storage

- Device ID: full public Bluetooth address, uppercase hexadecimal without separators (12 characters), read with `esp_read_mac(..., ESP_MAC_BT)`.
- Account ID: first 16 bytes of SHA-256 of the Firebase UID, encoded as 32 uppercase hexadecimal characters.
- Device owner: an account ID plus a random 32-byte owner secret, persisted as one NVS record. A power cycle does not erase ownership. No remote factory reset or ownership transfer command exists.
- Android stores account/device secrets and the current HTTP session encrypted with AES-GCM under an Android Keystore key. Ciphertext is bound to its UID/device preference key with authenticated additional data.
- Firestore `deviceCredentials/{deviceId}__{uid}` protects each credential. Only the owner may issue credentials; an additional driver can download their own credential only while the membership is ACTIVE. Primary owner credentials cannot be replaced. Keys are not included in public device claim records, logs, URLs, or UI.

Initial ownership assumes a trusted installation. Firmware does not independently consult Firebase to prove the first claimant owns the purchase. Protect initial USB setup and the pairing label; do not hand over an unclaimed device. Flash extraction/replacement is outside this prototype's protection.

## Pairing

Use LE Secure Connections, bonding, display-only I/O, and MITM-protected GATT permissions. A random six-digit pairing PIN is retained in NVS and printed to the physical serial console at BLE startup. The installer must record it on the device label. Android asks for it when a new phone pairs. Old Just Works bonds may need to be forgotten once after updating.

Android must negotiate MTU >= 128. Authorization commands are split to fit 125-byte characteristic payloads. No app or Wi-Fi credential command is accepted before cryptographic authentication on the current GATT connection.

## Additional-driver certificate

The owner's app computes a per-activation driver key:

```text
driverKey = HMAC-SHA256(ownerSecret, "DRIVER|deviceId|accountId|activationId")
certificate = HMAC-SHA256(ownerSecret, "CERT|deviceId|accountId|serial|driverKey")
```

Keys and MACs use 64 uppercase hex characters. HMAC keys are decoded to 32 raw bytes before use. `serial` is the decimal membership revision at invitation creation, 1 through 2147483647. Acceptance and subsequent state changes increment the membership revision but do not change the certificate's serial. Reinviting requires a new activation and a higher serial/key.

The certificate lets the device verify additional access offline without the owner being nearby or registering each phone in advance. Additional drivers receive their own key and certificate, never the owner's key. A certificate without possession of its driver key cannot answer a challenge.

## BLE exchange

All commands use the existing command characteristic; replies use the existing status characteristic.

| Command / reply | Behavior |
| --- | --- |
| `AUTH_HELLO` | Invalidate the connection's authentication and create a fresh random 16-byte nonce |
| `AUTH_CHALLENGE\|deviceId\|nonce\|NEW` or `...\|CLAIMED` | App checks the full device ID against the selected BLE address and cached credential |
| `CLAIM\|accountId\|ownerSecret` | Initial trusted owner enrollment only; persisted owner can never be overwritten; reply is a new challenge |
| `CERT1\|accountId\|serial\|driverKey` | Stage an additional-driver credential; reply `AUTH_CERT_PART` |
| `CERT2\|certificate` | Verify owner MAC, device identity, serial, and revocation floor; reply `AUTH_CERT_READY` |
| `AUTH\|accountId\|proof` | Verify `HMAC-SHA256(accountKey, "AUTH\|deviceId\|accountId\|nonce")`; consume nonce on success or failure |
| `AUTH_OK\|sessionToken` | New random 32-byte HTTP session token; app saves it securely before continuing |
| `REVOKE\|accountId\|serial` | Authenticated owner only; persist an increasing revocation floor; reply `AUTH_REVOKED` |
| `AUTH_ERROR\|reason` | Reject the operation; app closes GATT and asks the user to retry or verify access |

Challenges expire after 60 seconds. Authentication resets on disconnect. After `AUTH_OK`, the owner app first synchronizes its locally cached revocation floors, then sends `QUERY_CREDENTIALS`. Only then may `APPLY` or `CONNECT_SAVED` start Wi-Fi. Pending GATT writes are cleared on account changes and recheck the active account before dispatch.

## Camera handoff and HTTP

The existing Wi-Fi-to-camera software reboot carries the random session token in uninitialized `RTC_NOINIT_ATTR` memory, with no initializer. Initialized `RTC_DATA_ATTR` storage is unsuitable for this software-restart handoff. It is restored only when the existing single-use software-handoff marker is valid. Power cycles, watchdog/manual restarts, and unrelated software resets invalidate it and return to BLE setup. Ownership, pairing PIN, and revocations remain in NVS.

Every registered camera, image, control, status, heartbeat, warning, visibility, and disconnect handler requires:

```http
Authorization: Bearer <64-character session token>
```

Missing or incorrect credentials receive HTTP 401 before side effects, frame access, or watchdog renewal. The session token is compared without early exit and is never placed in a URL. A stream already open is the active monitoring session; revocation does not interrupt its warning path.

Authenticated responses include `X-DriveAlert-Device: <canonical device ID>` and `X-DriveAlert-Auth: 1`. Android requires both headers before accepting camera data or control responses. A saved address pointing to legacy firmware cannot silently bypass the authenticated protocol.

HTTP is still local plain HTTP on a trusted, WPA2/WPA3-protected hotspot/network, not TLS. This design does not promise confidentiality against a compromised local network or protection against physical flash access. A saved IP resolving to another board fails authorization because that board does not have the session token. The app never reassigns canonical identity from IP or mDNS discovery.

## Revocation and offline operation

The owner can revoke in the cloud while away from the device. The additional driver's app applies that change on its next sync. To enforce it on the ESP32 against an offline or modified client, the owner must reconnect over BLE and sync revocation floors. The app retains those floors locally, so this device sync itself can happen without mobile data after the cloud change has been downloaded.

The current firmware stores 32 distinct revoked account identities and fails explicitly if that capacity is exhausted; it never evicts a revocation to make room. For a currently pending/active credential, sync `serial - 1`; for a completed/removed credential, sync `serial`. Thus old certificates remain invalid after reinvitation. Do not promise instant remote revocation when the device is disconnected.

## Verification

- Android/JVM tests contain independent HMAC/account-ID vectors.
- `tests/device_authorization_test.cpp` runs the actual authorization implementation against host storage/HTTP adapters. Windows CNG supplies real HMAC-SHA256 for host tests; firmware uses Mbed TLS. Tests cover storage failure, owner replacement rejection, certificates, nonce replay/expiry, HTTP 401/session handoff, reboot, and persisted revocation.
- Firebase emulator tests exercise ownership races, atomic claims/events/credentials, privacy before acceptance, membership transitions, retry IDs, and immutable owner keys.
- Room migration and Android Keystore/UI tests run on an emulator. Physical PIN pairing, BLE MTU/notification timing, camera handoff, and speaker output still require the actual PCB.

Primary references: [Android cryptography](https://developer.android.com/privacy-and-security/cryptography), [Espressif HTTP headers](https://docs.espressif.com/projects/esp-idf/en/latest/esp32/api-reference/protocols/esp_http_server.html), [Firestore atomic operations](https://firebase.google.com/docs/firestore/manage-data/transactions).

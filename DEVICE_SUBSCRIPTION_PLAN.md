# Device ownership and simulated subscriptions

Status: implemented on the feature branch, with verification in progress. On 2026-11-04 the owner explicitly expanded scope to include an ESP32 firmware update. **The firmware-enforced design below supersedes the historical app-only proposal preserved later in this file.** Physical hardware verification is still required.

## Current firmware-enforced implementation

- Firestore owns device registration, owner-only demo activations, and driver invitation acceptance. Room caches verified account access through a non-destructive 6-to-7 migration.
- A random per-device owner key issues separate per-driver HMAC certificates. Credentials are encrypted on Android with the Keystore; Firestore permits additional drivers to download only their own ACTIVE credential.
- Firmware verifies a fresh challenge before accepting setup commands. New phones pair using a retained per-device PIN read by the installer over USB and placed on the device label.
- Every HTTP camera/control/output handler requires the random session obtained through authenticated BLE. Saved IPs and short hostnames do not bypass this check.
- No daily internet dependency or real payment integration is introduced. First registration, invitations, and cloud changes require internet.
- Device revocation takes effect after the owner synchronizes the change over Bluetooth. Already-downloaded revocations can be synchronized offline. Active monitoring is not interrupted.
- Trusted initial enrollment, secure handling of the label/PIN, a protected local Wi-Fi network, and physical firmware installation remain necessary. This is not protection against physical reflashing or a compromised phone/network.
- See [the authoritative protocol](drivealert-iot/DEVICE_AUTHORIZATION_PROTOCOL.md) and [release steps](drivealert-iot/deployment/DEVICE_SUBSCRIPTION_RELEASE.md).

## Historical app-only proposal (superseded by the firmware expansion above)

## Agreed scope

- Android enforces device access within the updated DriveAlert app.
- No ESP32 firmware update, real payment provider, or new paid backend is required for this prototype.
- Each device has one primary owner account included with its purchase.
- The owner can authorize additional driver accounts through a clearly labeled simulated subscription confirmation.
- Existing account connection codes identify invitation recipients.
- Initial sign-in, device registration, invitation management, and acceptance require internet. Previously authorized, signed-in drivers can reconnect and monitor offline, including after restarting the app or device.
- Trusted Contact relationships remain separate. Device membership does not grant access to another driver's records.
- Different accounts can use the same phone. Access follows the account UID and device identity, never the phone's Bluetooth bond alone.

## Explicit prototype limits

These are app-level permissions. Existing firmware accepts BLE setup and local camera requests without DriveAlert account authorization. An older unrestricted app, another BLE client, or direct HTTP access can bypass the new app's checks. No cryptographic authorization is sent to or verified by the ESP32 in this phase.

A BLE address identifies the device for this prototype; it is not a secret or proof of ownership. First registration means the first signed-in account to complete the online claim flow. A person with early physical access could claim an unregistered unit. Commercial ownership verification would need a separate provisioning design.

Offline authorization has no expiry in this demo. Removal takes effect on an offline phone when it next successfully refreshes its access. Immediate remote revocation is incompatible with unlimited disconnected use. Account sign-out removes the active in-memory authorization immediately on that phone.

The app allows one local monitoring session at a time and ends it on account changes. Without firmware enforcement or online coordination, it cannot guarantee exclusive use across two disconnected phones.

## Product decisions for version one

| Topic | Decision |
| --- | --- |
| Billing unit | One additional driver account on one device |
| Payer | Device owner |
| Confirmation | Explicit demo confirmation; no card, e-wallet, or payment collection |
| Validity | Active until the owner removes access or the driver leaves |
| Renewals and prices | No renewal timer or invented price in this prototype |
| Phone count | The same authorized account can use another phone after online sign-in and access refresh |
| Offline account switching | Out of scope; preserve restored Firebase sessions, but fresh sign-in requires internet |
| Ownership transfer | Out of scope; do not expose a reset or re-claim action that bypasses the owner |
| Device count | Registry supports multiple devices; retain the current one-active-device-per-driver behavior |
| Web portal | Keep existing Trusted Contact functionality; manage device subscriptions in Android |

## Owner flow

1. Sign in, scan for the nearby device, and select it.
2. Check its registered identity online before sending Wi-Fi setup commands.
3. For an unclaimed device, show **Register as primary driver** and require explicit confirmation. Create its ownership record atomically. Registration survives power cycles and reinstalling the app because it is stored in Firestore.
4. For an owned device, allow its owner and accepted additional drivers. Show **This device is registered to another account** to others without exposing the owner's identity.
5. Add **Manage Drivers** to the device screen. Show the included primary driver and additional memberships.
6. Select **Add Driver**, enter or paste the recipient's connection code, and verify the resolved name. Reject self-invitations, malformed/missing codes, and duplicate pending or active access before showing a subscription confirmation.
7. Show **Activate demo subscription?** with: **This activates access for one additional driver on this device. No payment will be collected.** Actions: **Cancel** and **Confirm Activation**.
8. Confirmation atomically records demo activation and creates the pending invitation. A failed request is not shown as activated. Retrying or double-tapping does not create duplicate activation events.
9. The owner may cancel a pending invitation or remove additional access while online. Neither action deletes the recipient's history or changes device ownership.

Version one assigns the demo subscription to the invited account rather than creating a reusable pool of paid slots. The UI can show how many additional accounts are active. This avoids inventing billing or slot-transfer rules before real payments exist.

## Additional driver flow

1. Create an account or sign in online. The account must support Driver mode; a Trusted Contact-only account must enable Driver mode through the existing role flow before accepting device access.
2. Open **Device Invitations**, verify the device label and inviter, and accept or decline. Acceptance must be by the target UID and must recheck that the invitation is still pending.
3. After acceptance, save the server-confirmed authorization locally for that account.
4. Scan and select the same physical device. Compare its BLE identity with the authorized device, then use the existing Wi-Fi provisioning and monitoring flow.
5. Maintain the driver's own calibration, preferences, sessions, and alerts. Device sharing does not copy the owner's setup or create a Trusted Contact relationship.

The recipient can accept before being near the device, but actual pairing/setup requires proximity. If access is removed between invitation and acceptance, acceptance fails and refreshes the displayed status.

## Device identity prerequisite

Current observations:

- `AndroidBleProvisioningClient` reports the full Bluetooth device address while scanning and in the successful provisioning event.
- `WifiProvisioningViewModel` currently saves `hostname ?: deviceId ?: ip` as the local device ID.
- Firmware hostname and advertised name contain only a 16-bit chip suffix. Those labels can collide; DHCP IP addresses also change.

Before implementing ownership, verify on the actual board that its full BLE address remains stable across power cycles, phone changes, and Wi-Fi setup. Use a normalized full address as the prototype's canonical registry key if that check passes. Store hostname, advertised name, and IP separately as connection/display metadata. Never claim a device by its short hostname or IP.

For existing Room records, add a separate canonical identity mapping without rewriting historical session IDs. Resolve old hostname/IP records by scanning the physical device. Existing records do not automatically imply ownership. Require one online registration or access verification before first use under the new model. If the existing firmware cannot expose a sufficiently stable identity, resolve that limitation before proceeding with registry enforcement.

## Proposed data model

### Firestore

`devices/{canonicalDeviceId}`

- `deviceId`, `claimed: true`, `createdAt`, `schemaVersion`
- Minimal exact-document lookup by authenticated users supports checking whether a device is claimed. Deny collection listing and all updates/deletes.
- Contains no owner UID or other personal details.

`deviceOwnership/{canonicalDeviceId}`

- `deviceId`, `ownerUserId`, `displayName`, `createdAt`, `schemaVersion`
- Owner and device ID immutable in version one. No client ownership transfer or deletion.
- Create atomically with the matching public claim record. Authorize reads only for its owner and associated pending/active recipients; deny unrelated reads and restrict list queries to the current owner.
- Registration checks the minimal claim record first. An existing claim never becomes a new claim merely because the caller cannot read its protected ownership record.

`deviceDriverAccess/{deviceId}__{driverUid}`

- `deviceId`, `ownerUserId`, `driverUserId`, `driverDisplayName`
- `status`: `PENDING`, `ACTIVE`, `DECLINED`, `CANCELLED`, `REMOVED`, or `LEFT`
- `revision`: positive integer, increased on every transition and reinvitation
- `targetConnectionCode`, `requestedAt`, `acceptedAt`, `endedAt`
- `billingMode`: `SIMULATED`; `demoActivationId`
- Deterministic identity prevents multiple memberships for the same account/device.
- Owner access derives from the ownership record and does not require an additional-driver membership.

`users/{ownerUid}/deviceSubscriptionEvents/{activationId}`

- `deviceId`, `ownerUserId`, `driverUserId`, `mode: SIMULATED`, `kind: ACTIVATION`, `createdAt`
- Immutable demo audit event, created by the owner in the invitation transaction.
- Activation IDs are stable across retries of the same operation. Reinviting after a completed decline/removal uses a new activation.
- Owner-scoped paths allow a caller to check an operation's existence even before the event document exists. Explicitly allow owner-only exact reads of missing events; a rule depending solely on nonexistent `resource.data` would break retry transactions.
- This is not a receipt, verified purchase, or production payment entitlement.

The separate minimal claim and protected ownership records let outsiders check claimed state without receiving owner details. Firestore rules cannot hide individual fields within a readable document. All creation transactions and rule validations must enforce consistency between these two records.

### Room

Add UID-scoped cached ownership/access records and canonical device mappings through a non-destructive schema migration from version 6 to 7. Cache only successful server-confirmed claims or accepted access, never pending writes or local confirmation clicks.

Keep Room authoritative for monitoring data. Firestore remains authoritative for ownership and membership, with an explicit local access projection for offline operation. **Repository correction:** Android currently enables Firestore disk persistence through `PersistentCacheSettings` in `DriveAlertApplication.kt`; the README's disabled-persistence statement is stale. The web portal uses a memory cache. Preserve those settings in this feature; the explicit Room projection distinguishes verified access from stale or pending Firestore data. Update the README's statement during implementation.

Do not store Wi-Fi passwords in the cloud registry or membership cache.

## Access checks and account isolation

Create a central device-access policy/repository instead of adding independent checks to buttons.

- Gate BLE connection/setup, credential submission, **Connect Saved**, saved-camera reconnect, preview, calibration, and monitoring entry points.
- Also gate the underlying service/repository operations so restored navigation or background callbacks cannot bypass the UI state.
- Verify both the current UID and canonical device ID. An allowed hostname or familiar IP must not authorize another device.
- While online, refresh access from the server. A transport failure preserves previously verified access; a confirmed removal/denial updates the local projection. Unknown accounts or devices need online verification.
- On sign-out/account change, stop monitoring, close stream and BLE sessions, cancel old observers/jobs, clear transient device/access state, and load only the new UID's records.
- Reject late async callbacks from a prior UID using an account/session generation check, including callbacks that save provisioned devices or grant access.
- Gate `DriveAlertMonitoringService` intent starts and `START_STICKY` process-death recovery, independently of Compose navigation. Resolve the initialized Firebase identity and compare it with both the recovery/intent UID and Room access. A recovery record is not authorization. Missing identity, mismatched UID, unresolved legacy device identity, or denied access closes the stored session as interrupted without starting the stream. Preserve same-boot recovery for an authorized account; device reboot still uses the existing interrupted-session policy.
- When removal is received during monitoring, keep the active safety session running and block subsequent sessions; explicit sign-out/account switching ends the active session.
- App reinstall/new phone requires online verification again. No Firebase credentials, password cache, or offline account picker is added.

## Firestore rules and transactions

- Atomically claim an unowned canonical ID. Two simultaneous claims must yield exactly one owner; no overwrite or ownership mutation is allowed.
- Only the owner can activate demo access, invite, cancel, or remove a driver for their device.
- Validate the entered connection code against the recipient UID and validate document fields, immutable identities, server timestamps, and allowed state transitions.
- Only the recipient can accept/decline a pending invitation or leave their own active membership. They cannot change the device, recipient, owner, or activation reference.
- Link invitation creation and its demo activation with `getAfter` checks in a transaction/batch. Prevent creating one without the other or modifying audit events afterward.
- Scope invitation/list queries to the current owner's or recipient's UID. Deny unrelated membership reads and collection-wide account/device enumeration.
- Refactor any reused code-resolution helper without altering existing Driver–Trusted Contact permissions.
- Use local emulators during development. Cloud deployment is a separate release step requiring explicit authorization; merging code does not enable these collections on the live project.

## Research audit: contracts to implement precisely

### Server-confirmed access and offline behavior

Firestore can emit cached snapshots and local writes before a server round trip. For access listeners, use metadata changes and accept a new grant only when `isFromCache == false` and `hasPendingWrites == false`, or after a successful explicit server read. The SDK's cache alone is not a new authorization. A server-confirmed ownership/membership document must also pass the current UID and canonical-ID checks before updating Room. [Firestore offline behavior](https://firebase.google.com/docs/firestore/manage-data/enable-offline), [Android snapshot metadata](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/SnapshotMetadata).

On startup, evaluate the account's Room grant without waiting for an internet probe. Run a bounded server refresh in the background. A hotspot with no mobile data must not delay local monitoring while a request times out. Cached Firestore snapshots can update display state but cannot resurrect a removed grant. Store an integer membership `revision`, increment it on every transition, and retain a removal/leave tombstone locally; ignore older or cache-only grants after observing a newer revision. Membership documents are never deleted in version one.

| Refresh result | Next local action |
| --- | --- |
| Valid server-confirmed owner/ACTIVE membership | Save/update grant and allow next session |
| Server-confirmed non-active/missing membership | Save denial/tombstone and block next session |
| UNAVAILABLE, network loss, or bounded timeout | Keep the last verified grant; unknown device stays unverified |
| UNAUTHENTICATED after SDK refresh fails | Require sign-in for new sessions; do not treat it as device removal |
| PERMISSION_DENIED | Suspend new starts pending verification and show access/service error; do not silently reassign ownership or label it as definite removal |
| Cache-only empty query or cached PENDING/ACTIVE result | No new grant or revocation decision |
| UID changes while any request is pending | Ignore the old result and cancel its operations |

An expired Firebase token must not be used as a daily local-monitoring expiry timer. Firebase notes that `currentUser` can exist even when its token is invalid; local offline access and online authentication are separate states. Fresh authenticated cloud operations still need valid authentication. Do not make local BLE/stream/monitoring calls depend on refreshing an ID token. [Firebase user state](https://firebase.google.com/docs/auth/android/manage-users).

### Transaction, retry, and state-transition contracts

Firestore transactions can rerun their callbacks, require reads before writes, and fail offline. Generate operation IDs outside the transaction callback. Do not show success, update Room, send notifications, or mutate Compose state inside the callback. Claims, invites, acceptance, removal, and leave operations use transactions and report success only after server acknowledgement. All affected reads precede writes. [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions).

- Claim: read the minimal claim record; if absent, create it and its protected ownership record atomically. Rules must reject an orphan record and reject replacing either existing record. An owner retry resolves its already committed claim; it never becomes a transfer.
- Invite: pre-resolve the code for display, then revalidate the mapping inside the write transaction/rules. Read ownership, existing membership, target code, and owner-scoped operation event before writes. Only absent or completed memberships can become PENDING. Reuse the operation ID after an uncertain network outcome; reconcile its event/membership rather than creating a second activation.
- Accept/decline: only PENDING transitions are accepted. A simultaneous owner cancellation/removal and recipient acceptance serialize through the same membership document. The losing operation refreshes the final status rather than displaying optimistic success.
- Remove/cancel/leave: preserve immutable identity fields and activation history; increment membership revision. Repeated requests return the existing final state without duplicating events.
- Reinvite: use a new activation ID and higher revision. The previous acceptance cannot authorize the new pending invitation.
- Commit succeeds but Room write fails: do not offer offline use yet. Show that online access exists but offline setup needs completion; retry the server read and local transaction. Process death after commit resolves the same way on next launch.
- The demo records a subscription activation, not a payment settlement. A cancellation/decline ends its access attempt; a new activation is required for another invite. No refund or monetary balance exists.

The owner may directly write correctly shaped demo activations under the rules because confirmations intentionally simulate purchase. This does not establish a trusted billing backend and must not be reused for future real payments.

### Query and rule feasibility

Specify separate `get` and `list` rules. Owner membership lists query `deviceId == selectedDeviceId` and `ownerUserId == currentUid`; recipient lists query `driverUserId == currentUid`, with client-side grouping for status in version one. Owner device lists query `ownerUserId == currentUid` on the private ownership collection. Deterministic membership gets authorize the encoded recipient UID even when the document is absent, or the verified device owner. Owner-only event gets use their `/users/{ownerUid}/...` path. UI sorting is local for these small lists, avoiding unnecessary compound order indexes.

Firestore rules do not filter broad queries: every query must satisfy its permission constraints. Validate the exact repository queries against rules before building screens. Keep a per-operation rules dependency budget below 10 document accesses and below 20 for the full transaction; use `getAfter` for cross-document invariants and inspect emulator coverage/traces for missing-document errors or unnecessary accesses. [Rules conditions and limits](https://firebase.google.com/docs/firestore/security/rules-conditions), [Rules emulator tests](https://firebase.google.com/docs/firestore/security/test-rules-emulator).

Do not rely on rendering a recipient as a Driver to enforce eligibility. On invitation acceptance, validate their active account and Driver/BOTH capability from the recipient profile in rules. Driver setup/role changes occur first through the existing role flow. Membership and ownership documents use exact allowed-field sets, canonical document IDs, server timestamps, and immutable participant fields.

### Device and endpoint identity limits

Android exposes Bluetooth device addresses, but Bluetooth LE also supports private/random addresses. Do not assume any discovered address is permanent or derive the Bluetooth address from the Wi-Fi MAC. Verify the actual Bluedroid build and hardware behavior. Keep SDK 34 compatibility: do not introduce newer identity-address APIs or privileged Bluetooth permissions. Normalize a verified public/stable address to an uppercase 12-hex-character device key. [Android BluetoothDevice reference](https://developer.android.com/reference/android/bluetooth/BluetoothDevice), [Espressif address types and discovery](https://docs.espressif.com/projects/esp-idf/en/v5.5.6/esp32/api-guides/ble/get-started/ble-device-discovery.html).

The existing `DriveAlertEndpointResolver` accepts a reachable saved IP and can rediscover by short hostname. **Neither proves that the camera endpoint is the registered physical board.** Keep canonical authorization separate from these routing hints. Require a user-selected BLE target for first setup and when identity mapping is unresolved, disable wildcard discovery for ownership-controlled records, and never create/change a canonical mapping from an IP, TCP response, or mDNS label. Ambiguous targets require explicit BLE setup. Routine saved-endpoint reconnect remains best-effort within a trusted local network; it cannot guarantee physical identity against IP reuse, collisions, or spoofing without a firmware protocol change. This is an explicit limitation, not an acceptance claim that the app can prove otherwise.

Before allowing any credential writes after account switching or refreshing permission, check the same `(uid, deviceId, accountGeneration)` again at dispatch. Clear queued writes and invalidate GATT callbacks before the next account starts. Cleanup must not send a delayed global disconnect after a newer account has established its session.

### Existing installations and release sequencing

- Add access tables and mapping tables without changing historical device IDs or deleting existing calibration/session rows. A full migration from any supported earlier schema must reach version 7 with data intact. Use the repository's pinned Room version and existing `MigrationTestHelper`; do not upgrade to the newer Room APIs used in current documentation examples. No destructive migration fallback. [Room migration guidance](https://developer.android.com/training/data-storage/room/migrating-db-versions).
- Existing users need one online claim/access verification after the feature upgrade; this is different from requiring daily internet. Show that requirement before enabling the new access controls.
- Test new rules alongside every existing Trusted Contact rule. Prepare rules/index deployment, wait for any required index to be ready, and verify a controlled account can claim/invite/accept before distributing the access-enforcing APK. Owner authorization is required for the live deployment, but it should be requested only after the concrete tested release is ready.
- If the backend is not ready, show a setup/service error; never fall back to unrestricted provisioning or interpret denied reads as an unclaimed device. Rolling back to an old APK bypasses app enforcement, as already acknowledged.
- Keep the prototype deployment visibly in simulated-billing mode; no pricing or production-payment implication.

## Implementation order

1. **Identity verification:** verify real BLE identity stability, trace every device entry point, and finalize claim/privacy rules. No firmware flashing.
2. **Registry and rules:** implement atomic ownership, membership transitions, demo activation events, and emulator tests.
3. **Offline access:** add Room migration, server-to-local projections, central access policy, and legacy-device mapping.
4. **Owner and invitation screens:** registration, Manage Drivers, code lookup, demo confirmation, accept/decline/cancel/remove/leave states, and clear offline messages.
5. **Connection integration:** gate all BLE/Wi-Fi/camera/monitoring paths and harden account-change cleanup and asynchronous races.
6. **Verification and handoff:** run the checks below, demonstrate on the actual board, and document the app-level and offline-revocation limits. Prepare Firebase rules/index changes for the separately authorized deployment.

## Acceptance checks

- A claims a device; B cannot claim or use it through the updated app without accepted access.
- Concurrent claims, duplicate confirmations, self-invites, repeated invitations, unauthorized acceptance, and attempts to rewrite ownership are rejected.
- Cancelling confirmation creates no activation or invitation. Retrying a failed request creates no duplicates.
- Pending, declined, removed, and cancelled memberships do not grant access. Owner access remains included.
- B accepts A's invite and can provision and monitor under B's UID; B cannot view A's calibration or history.
- Switch A to unauthorized C on the same phone: old BLE/stream access and late callbacks cannot grant C access. C also cannot bypass checks through saved connections, preview, calibration, or restored navigation.
- With internet disabled but local Bluetooth/Wi-Fi available, an authorized signed-in owner or additional driver reconnects and monitors after app and ESP32 restarts.
- Fresh login/claim/invitation requires internet and reports that clearly. An offline lookup failure does not activate access.
- Removal refresh blocks the next session, preserves history, and leaves active monitoring uninterrupted until stopped. A disconnected phone retains its last verified permission until refresh, as documented.
- DHCP address changes do not create a new ownership record. Short-name collisions never create or replace canonical authorization mappings. Saved-IP physical endpoint identity remains subject to the explicitly documented firmware limitation.
- Room 6-to-7 migration preserves existing records and leaves unresolved old connections pending explicit identity verification.
- Android unit tests, Room migration/instrumentation checks, build, and affected UI tests pass. Firebase rules tests cover all ownership and membership transitions. Existing Trusted Contact and web regression checks remain green.

## Failure-injection and hardware release gates

Implementation is not complete merely because normal-path UI tests pass. Record pass/fail evidence for these cases:

| Area | Required scenarios |
| --- | --- |
| Device identity | Ten power cycles, app process restart, forget/re-pair, two Android phones; compare full BLE address; inspect saved mapping after DHCP change |
| Claims | Two accounts claim concurrently; lose response after successful commit; retry; crash between commit and local save; attempt orphan/public-only/private-only claim writes |
| Subscription | Double tap confirmation; cancel; disconnect before commit; uncertain response; reinvite after decline/remove; retry same operation ID after app restart |
| Membership races | Accept vs cancel/remove; accept twice; driver leaves vs owner removes; old invite response arrives after reinvite |
| Rules | Outsider direct get/list/write, nonexistent-document get, wrong target code/UID, changed owner/device fields, forged ACTIVE state, missing activation event, excessive-field writes |
| Shared phone | A signs out during lookup, claim, BLE connect, queued credential writes, stream resolve, and monitoring start; B must receive none of A's success callbacks or data |
| Offline startup | Cold launch with restored account and Room grant, no mobile data, hotspot/local Wi-Fi available, server unreachable; start without waiting for token/network refresh |
| Revocation | Remove while recipient offline; verify documented stale-access behavior; reconnect and observe denial; replay older cached ACTIVE snapshot; remove during monitoring and block next session |
| Service | Valid same-boot process-death recovery; recovery after sign-out/different UID; stale intent; no initialized auth state; removed access; reboot marks interrupted rather than blindly resuming |
| Storage | Upgrade existing schema with records; unresolved legacy mapping; simulate Room cache-write failure after cloud success; verify another UID cannot consume the grant |
| Camera routing | Old reachable IP, changed DHCP IP, hostname collision, null hostname, different nearby board; no automatic canonical identity reassignment |
| Release | Local rules coverage, existing connection regression tests, controlled live smoke test after separately authorized deployment, physical offline owner and invited-driver demonstration |

Use a second board if available for collision/routing cases; otherwise simulate endpoint responses and state that physical multi-board behavior remains unverified. Hardware tests do not require removing the ESP32 from the PCB or updating firmware. Until the identity and two-account offline hardware gates pass, describe the result as implemented/tested in software rather than proven on the device.

## Release boundary

This phase demonstrates the business model with app-enforced account access and honest simulated subscriptions. Device-enforced cryptographic ownership, OTA support, real recurring billing, subscription expiry, immediate offline revocation, offline account switching, and ownership transfer require separate designs and are not prerequisites for this prototype.

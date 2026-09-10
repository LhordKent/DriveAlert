# DriveAlert Free Connection-Code Plan

Status: implemented in Android and covered by local Firestore emulator tests. The checked-in Firebase rules remain intentionally undeployed until the project owner explicitly approves deployment.

## Goal

Replace the undeployed email-lookup Cloud Function with a Cloud Firestore-only connection-code workflow that works on Firebase's free Spark plan. Either a Driver or a Trusted Contact can initiate a relationship. Room, Stage 3 synchronization, and the future-only sharing boundary remain unchanged.

## User flow

1. After its Firestore profile exists, every account receives a random DriveAlert connection code.
2. Account displays `My connection code` with copy and system-share actions.
3. Driver view offers `Connect a Trusted Contact`; Trusted Contact view offers `Connect a Driver`.
4. The initiator enters the other account's code.
5. DriveAlert resolves that exact code and displays the target name for confirmation.
6. The initiator sends an in-app request.
7. The recipient accepts or declines. Only the initiator can cancel a pending request.
8. Either participant can disconnect an approved relationship.
9. Approval shares only eligible Stage 3 records with `periodStartedAt >= approvedAt` while the connection remains approved.

Users can share a code through Messenger, SMS, email, or any other existing app. DriveAlert itself does not send external messages.

## Code format

- Generate at least 128 random bits with Android `SecureRandom`.
- Use an unambiguous uppercase alphabet and group it for readability, for example `DA-7K9M-4R2X-P8QW-6T3N-5VCH`.
- Normalize input by trimming whitespace, removing hyphens, and uppercasing.
- Never derive a code from UID, email, name, or phone number.
- Retry the generation transaction if the Firestore document ID already exists.
- Code rotation can be added later and must not alter existing relationships.

## Firestore model

### `users/{uid}` additions

```text
connectionCode: string
connectionCodeCreatedAt: timestamp
```

Existing Firebase Authentication accounts without a Firestore profile must be backfilled on first post-authenticated launch.

### `connectionCodes/{normalizedCode}`

```text
ownerUserId: string
displayName: string
createdAt: timestamp
active: boolean
```

Authenticated clients may fetch one exact code document. Collection listing must be denied.

### `trustedContactConnections/{driverUid}__{trustedContactUid}`

Retain the current connection fields and add `targetConnectionCode` so rules can verify that the entered code belongs to the other participant.

Relationship sides are determined by the initiator's active view:

- Driver view: current UID is Driver; code owner is Trusted Contact.
- Trusted Contact view: code owner is Driver; current UID is Trusted Contact.

## Firestore rule requirements

- A user may create and maintain only their own profile and code mapping.
- A code mapping must point to the authenticated creator.
- Allow authenticated exact-document code lookup; deny code collection listing.
- Connection creation requires `requestedByUserId == request.auth.uid`.
- The creator must be one participant, and `targetConnectionCode` must resolve to the other participant.
- Driver UID, Trusted Contact UID, requester, target code, and request time become immutable.
- Only the recipient may approve or decline.
- Only the initiator may cancel a pending request.
- Either participant may revoke an approved connection.
- Shared Stage 3 reads continue to require current approval and `approvedAt <= periodStartedAt`.

No Cloud Function, Blaze plan, service-account key, Realtime Database, or custom server is required.

## Future Android implementation slices

1. Backfill missing Firestore profiles for existing authenticated accounts.
2. Generate and display connection codes.
3. Add code lookup rules and Firebase Emulator tests.
4. Replace email fields with code-entry UI in both views.
5. Create requests directly through Firestore with both-direction support.
6. Preserve recipient/sender transition permissions and future-only Stage 3 sharing.
7. Remove the Firebase Functions Android dependency and callable invitation code.
8. Remove or archive the Functions workspace after all replacement tests pass.
9. Deploy only reviewed Firestore rules and indexes after explicit approval.

## Required tests

- Code generation, normalization, and collision retry.
- Existing-profile backfill.
- Invalid, missing, self, and duplicate codes.
- Driver-initiated and Trusted Contact-initiated requests.
- Recipient-only approval/decline and initiator-only cancellation.
- Disconnect behavior.
- Exact code lookup allowed and code listing denied.
- Future-only Stage 3 access and denial after revocation.
- Existing UID-isolated Room tests.
- Android unit, build, instrumentation, and Firebase Emulator suites.

## Non-goals

- No email lookup or email delivery.
- No paid Firebase services.
- No changes to monitoring, calibration, IoT, AI, or Warning Stage calculation.
- No production Room or Firestore seed data.

## Completion criteria

Two real Firebase accounts on separate app installs can exchange a code, request and approve a relationship in either direction, and access only eligible future Stage 3 records while remaining on the Spark plan.

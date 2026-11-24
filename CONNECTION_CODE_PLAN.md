# DriveAlert Free Connection-Code Plan

Status: the one-way invitation portal and Firestore rules went live on 2026-11-24. The tested Android APK was installed in place on the TECNO LE6h.

## Goal

Use Cloud Firestore on Firebase's free Spark plan. The Driver invites a Trusted Contact. Eligible Stage 3 sharing starts after the contact accepts.

## User flow

1. After its Firestore profile exists, every account receives a random DriveAlert connection code.
2. Account displays `My connection code` with copy and system-share actions.
3. Driver view offers `Invite a Trusted Contact`.
4. The Driver enters the Trusted Contact's code.
5. DriveAlert resolves that exact code and displays the target name for confirmation.
6. The Driver sends an in-app request.
7. The contact accepts or declines in Android Trusted Contact view or on the web. The Driver can cancel while the request is pending.
8. Either participant can disconnect an approved relationship.
9. Approval shares only eligible Stage 3 records with `periodStartedAt >= approvedAt` while the connection remains approved.

Users can share a code through Messenger, SMS, email, or any other existing app. DriveAlert itself does not send external messages.
Driver accounts keep their codes for the separate additional-driver camera access flow.

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

The existing `targetConnectionCode` field identifies the code used for the invitation. The sender UID is the Driver UID and the code owner is the Trusted Contact UID.

## Firestore rule requirements

- A user may create and maintain only their own profile and code mapping.
- A code mapping must point to the authenticated creator.
- Allow authenticated exact-document code lookup; deny code collection listing.
- Creation and re-invitation require a verified sender whose UID is both `requestedByUserId` and `driverUserId`. The sender needs Driver capability and the active target code must belong to the recipient with Trusted Contact capability. `BOTH` grants both capabilities.
- The participant UIDs stay fixed. The Driver can reuse the existing relationship ID after decline or revocation.
- The named Trusted Contact may accept or decline a Driver invitation. The Driver may cancel their pending invitation.
- Either participant may revoke an approved connection.
- Shared Stage 3 reads continue to require current approval and `approvedAt <= periodStartedAt`.
- Existing approved relationships keep their original approval dates. An old pending request sent by the contact stays hidden and can be replaced atomically by a fresh Driver invitation. The replacement refreshes the Driver name and email and requires the contact to accept.

No Cloud Function, Blaze plan, service-account key, Realtime Database, or custom server is required.

## Required tests

- Code generation, normalization, and collision retry.
- Existing-profile backfill.
- Invalid, missing, self, and duplicate codes.
- Driver-sent invitations, blocked reverse requests, and handling of previously pending or approved contact-sent relationships.
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

A Driver can use a contact’s code to send an invitation on Android. The Trusted Contact can accept on Android or the web, then access eligible future Stage 3 records while remaining on the Spark plan.

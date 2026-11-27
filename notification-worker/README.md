# DriveAlert notification worker

Cloudflare Worker for Trusted Contact FCM delivery and Driver email lookup. Android and Firestore own the Stage 3 records.

## Setup

1. Create a Cloudflare D1 database named `drivealert-notifications` and replace the ID in `wrangler.toml`.
2. Create a Firebase service account with Firestore read-only and Firebase Cloud Messaging API Admin roles.
3. Store its values as Worker secrets; never commit the JSON key:

```text
wrangler secret put FIREBASE_CLIENT_EMAIL
wrangler secret put FIREBASE_PRIVATE_KEY
```

4. Run `npm install`, `npm run d1:migrate:remote`, and `npm run deploy`.
5. Put the deployed URL in the Android Gradle property `DRIVEALERT_NOTIFICATION_WORKER_URL`.

The Worker verifies Firebase ID tokens, re-reads records and relationships from Firestore, groups chunked backlogs, and tracks delivery independently per installation.

## Invitation email lookup setup

1. Use a separate Google Cloud service account with the `firebaseauth.users.get` permission on the Firebase project. Configure `FIREBASE_LOOKUP_CLIENT_EMAIL` and `FIREBASE_LOOKUP_PRIVATE_KEY` as Worker secrets.
2. Apply D1 migrations `0002_trusted_contact_email_lookup.sql` and `0003_device_driver_email_lookup.sql` before publishing the Worker.
3. Release the Worker and the Android and web updates together. Android uses its configured Worker URL for notification dispatch and both lookup flows.

Verified Drivers can search for an eligible account by exact email. The recipient needs an active, verified account with the role required for that invitation. D1 stores short-lived, requester-bound matches with a fingerprint of the email. Trusted Contact invitations use the driver's Firebase token and existing Firestore rules.

Additional-driver searches also require primary ownership of the selected device. The search result contains the name, verified email, and an opaque match ID. On confirmation, Android resolves that match for the same device and uses its existing signed device-invitation transaction. The additional driver accepts in **Subscriptions & Drivers**.

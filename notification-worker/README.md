# DriveAlert notification worker

Cloudflare Worker used only for Trusted Contact FCM delivery. Stage 3 records remain owned by Android and Firestore.

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

# DriveAlert Trusted Contact Web Portal

Responsive React portal for Trusted Contacts to manage Driver relationships and review eligible synchronized Stage 3 boundary records. It uses the existing `drivealert-lhordkent` Firebase Authentication identities and Firestore contract.

The portal supports email/password authentication only. It does not provide Google sign-in, live monitoring, location, emergency response, device controls, Driver Alert History, or Trusted Contact push notifications.

## History retention

Settings → History & Storage offers 1, 3, 7, custom 1–3,650 days, and Keep until I delete (the default). Save applies the choice; shorter periods preview the number of currently loaded records affected and require confirmation. Individual records can be deleted from their detail page, and each driver's shared history can be cleared separately.

These controls remove records only from the signed-in account's view in this browser. They never delete Firestore source records or another contact's history. IndexedDB stores account-scoped preferences, expiry cutoffs, and manual dismissals, preventing records from returning after reload or a longer retention choice. Dashboard totals and unread badges use the retained records. Other tabs update through BroadcastChannel; browsers without it refresh their history on focus or the next cleanup tick.

Age uses the original event time in 24-hour days. Cleanup runs while the portal is open and on returning to it; it cannot run while the browser is closed. Android and other browsers use independent settings. Clearing site data resets these local choices and removals. If browser storage cannot be read, history is withheld and a retry action is available in History & Storage.

## Configuration

Copy `.env.example` to `.env.local` and supply the registered DriveAlert Web App values from the existing `drivealert-lhordkent` Firebase project. Do not commit `.env.local`.

`VITE_FIREBASE_DATABASE_URL` is retained only as supplied Firebase project metadata. The portal initializes Cloud Firestore and does not initialize or use Firebase Realtime Database.

## Commands

```powershell
cd drivealert-web
npm ci
npm run dev
```

Run locally against Firebase emulators in two terminals. From the repository root:

```powershell
$env:JAVA_HOME = "C:\Path\To\JDK-21-Or-Newer"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
firebase emulators:start --project drivealert-lhordkent --only auth,firestore
```

Then, from `drivealert-web`:

```powershell
$env:VITE_USE_FIREBASE_EMULATORS = "true"
$env:VITE_FIREBASE_PROJECT_ID = "drivealert-lhordkent"
npm run dev
```

Quality gates:

```powershell
cd drivealert-web
npm run typecheck
npm run lint
npm test
npm run build
```

Run every web, Firebase security, and browser test (with JDK 21+ configured):

```powershell
cd drivealert-web
npm run test:all
```

Emulator-backed browser and accessibility tests:

```powershell
$env:JAVA_HOME = "C:\Path\To\JDK-21-Or-Newer"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd drivealert-web
npx playwright install chromium
npm run test:e2e:emulator
npm run test:a11y
```

Preview the production bundle:

```powershell
cd drivealert-web
npm run build
npm run preview
```

## Security status

The checked-in `firestore.rules` file is the portal's authorization contract and is verified locally with the Firebase Emulator Suite. This repository documents that the live project still has test-mode rules. Neither the portal nor the Firestore rules are deployed automatically, and the live environment must not be described as production-secure until the reviewed rules are explicitly deployed and verified by the project owner.

# History retention

Drivers and Trusted Contacts use Settings → History & Storage. Choices are 1, 3, 7, custom 1–3,650 days, and Keep until I delete (the default). Preferences and personal shared-history removals belong to the signed-in account on this phone.

Age uses the original event timestamp and 24-hour days. A shorter period requires a preview and confirmation; its count includes deletable local alerts and currently loaded shared records. Older shared records arriving later are also hidden. Increasing retention or choosing manual deletion never restores removed history.

Local alert deletion cascades to alert signs. Monitoring sessions, calibration, device setup, synchronization records, and delivery queues are retained. Active and paused sessions are excluded from deletion; session completion runs deferred cleanup. Android system notifications are outside this feature.

Shared records remain in the driver's cloud data. A monotonic local expiry cutoff prevents expired shared records returning after refresh or policy changes. Manual dismissals are keyed by viewer, source driver, and record ID. Clearing shared history affects only the selected driver's currently displayed records for that viewer.

Cleanup runs on foreground entry, history entry, settings changes, once per minute while the app is visible, after a drive ends, and through unique hourly WorkManager work. Background timing follows Android scheduling. The Room 4→5 migration preserves all existing data and defaults retention to manual.

## Direction contract

THESIS: Extend the existing settings and history views with a single understandable retention choice and explicit destructive actions.

OWN-WORLD: Inherit DriveAlert's Material controls, warm charcoal surfaces, system typography, existing spacing, and restrained red actions. No new design system or assets.

STORY: Both roles find the same setting, choose a duration, review the impact, and save. Manual deletion remains available in either mode.

FIRST VIEWPORT: Standard back navigation and History & Storage title, brief local-scope explanation, then full-width radio rows. Custom input follows its selection; explanatory text and Save follow below in the scrollable content.

FORM: Precisely specified settings extension; no concept seed required. The screen inherits the incumbent Android visual system.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Web portal

The Trusted Contact web portal has the same duration choices and explicit Save/confirmation flow under Settings → History & Storage. It offers per-record removal and per-driver clearing for shared records. The portal does not provide local Driver Alert History or live monitoring.

Browser IndexedDB transactions persist each account's policy, monotonic expiry cutoff, and source-driver/record dismissal keys. These are personal visibility controls; Firestore source records are never changed. All displayed record counts and unread badges use the filtered history. Browser storage failure withholds history until the user can retry. Clearing site data resets local policy and dismissal state. Android and other browsers remain independent.

Cleanup runs on opening/returning, every minute while visible, and after preference changes. BroadcastChannel propagates explicit changes between tabs; focus and timer refreshes cover older browsers. Closed browsers cannot run cleanup.

### Web direction contract

THESIS: Extend the existing portal's settings with an ordinary retention form and explicit personal deletion actions, preserving the portal's Trusted Contact scope.

OWN-WORLD: Use existing charcoal/red tokens, typography, surface, field, Button, PageHeader, and ConfirmDialog components. No new visual world or raster assets.

STORY: Choose a duration, review its effect, and save. Removing shared history affects only the viewer's current browser, without touching the source driver's records.

FIRST VIEWPORT: Standard portal navigation and back link, History & Storage heading, then a bounded column of radio choices. Custom input and explanation precede Save. Controls wrap on compact screens.

FORM: Precisely specified local extension of the incumbent settings system, without a concept seed.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Verification

Unit tests cover durations, boundaries, policy changes, and source-driver isolation. Device tests cover migration, database reopen, account isolation, active-drive protection, deferred cleanup, pending synchronization, preference preservation, both roles, custom input, confirmation, and large-font dark-theme screenshots.

Validated on the TECNO_LE6h: 226 unit tests and 21 database, migration, and UI instrumentation tests passed. The debug build passed and was installed with `adb install -r`. Native screenshots cover dark theme at 1.3 font scale, numeric input, and keyboard dismissal with Back. The finish reviewer cleared the manual-mode copy correction; the documentation review confirmed the incumbent DESIGN.md can remain unchanged. Tablet layouts and production predictive-Back behavior were not separately verified.

Web validation: typecheck, lint, 66 unit/component tests, the production build, and all 11 emulator-backed Chromium tests passed. Coverage includes original-event expiry, reload persistence, retention increases, individual/per-driver removal, cross-tab updates, account isolation, source-record preservation, storage failures, and busy-dialog keyboard focus. Desktop (1440px) and mobile (390px) captures passed automated WCAG A/AA checks without horizontal overflow. The finish review cleared the dialog focus correction and approved the existing visual system; DESIGN.md remains unchanged and no raster assets ship. These checks validate the local implementation; the web update has not been deployed.

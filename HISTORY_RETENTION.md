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

## Verification

Unit tests cover durations, boundaries, policy changes, and source-driver isolation. Device tests cover migration, database reopen, account isolation, active-drive protection, deferred cleanup, pending synchronization, preference preservation, both roles, custom input, confirmation, and large-font dark-theme screenshots.

Validated on the TECNO_LE6h: 226 unit tests and 21 database, migration, and UI instrumentation tests passed. The debug build passed and was installed with `adb install -r`. Native screenshots cover dark theme at 1.3 font scale, numeric input, and keyboard dismissal with Back. The finish reviewer cleared the manual-mode copy correction; the documentation review confirmed the incumbent DESIGN.md can remain unchanged. Tablet layouts and production predictive-Back behavior were not separately verified.

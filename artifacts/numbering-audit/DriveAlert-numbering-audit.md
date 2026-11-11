# DriveAlert table and figure numbering audit

Audit date: 9 November 2026

Source: C:\Users\Admin\Downloads\DriveAlertlatest-codex-fixes-tables-page142.docx

The front-matter lists need nine definite corrections: eight missing entries and one incorrect page reference. The body has no duplicate exact caption identifiers or missing parent-number groups. Continuation labels use inconsistent conventions. The source DOCX was opened read-only and was not modified.

## Definite corrections

Add the following entries to the List of Tables or List of Figures. Pages below are the printed manuscript page numbers, not the PDF viewer page numbers.

| Entry | Caption title | Printed page |
|---|---|---:|
| Table 33 | Unit Testing | 142 |
| Table 34 | Integration Testing | 150 |
| Table 35 | Alpha Testing | 152 |
| Table 36 | Acceptance Testing | 153 |
| Table 37 | Cost Specification | 156 |
| Table 38 | Human Specification | 159 |
| Figure 63 | DriveAlert Deployment Diagram | 155 |
| Figure 64 | Project Road Map | 178 |

In the List of Figures, change Figure 6.1 Functional Decomposition Diagram from page 58 to page 57. The diagram and its caption are both on printed page 57. Figure 6.2 correctly remains on page 58.

## Continuation labels to standardize

The List of Tables uses parent numbers 1, 3, 5, 6, and 32, but the corresponding body captions begin at 1.1, 3.1, 5.1, 6.1, and 32.1. Other groups start with the unsuffixed number and then add decimal continuation numbers. This is a consistency issue; the school formatting guide was not supplied, so a particular convention cannot be declared mandatory.

| Logical table | Current body labels |
|---|---|
| Comparative Matrix | 1.1, 1.2, 1.3 |
| Agile Sprint Plan | 3.1 through 3.5 |
| Validation Board | 5.1, 5.2 |
| Gantt Chart | 6.1 through 6.5 |
| List of Modules | 32.1, 32.2 |
| TrustedContactConnection | 9, 9.1 |
| MonitoringSession | 14, 14.1 |
| StageSyncRecord | 17, 17.1 |
| MonitoringLogEvent | 20, 20.1 |
| DeviceDriverAccess | 24, 24.1 |
| Unit Testing | 33, 33.1 through 33.7 |
| Integration Testing | 34, 34.1 |

Suggested convention, subject to school requirements: use Table N for the first part and Table N continued for subsequent parts, with one parent entry in the List of Tables. If decimal parts are required, use them consistently from the first part and align the list entries with that convention. Do not renumber unrelated later tables merely because a table spans multiple pages.

Figure groups 6.1 through 6.3 and 7.1 through 7.2 match their list labels and are in order. No figure identifier correction is indicated by those subdivisions alone.

## Checks completed

- Found 38 logical numbered table groups, represented by 63 captioned parts; the list contains only 32 parent entries.
- Found 64 logical numbered figure groups, represented by 67 captions; the list contains 65 entries through parent Figure 62.
- Checked all 32 listed table start-page references; all match when decimal parts are treated as parts of the listed parent table.
- Checked all 65 listed figure entries; 64 page references match, and Figure 6.1 requires the correction above. Listed figure titles match the body captions after whitespace normalization.
- Checked explicit numbered references in the main document text against existing caption identifiers; none point to a nonexistent caption.
- No duplicate exact body caption identifiers were found. Parent sequences cover Tables 1 through 38 and Figures 1 through 64.
- Captions and the front-matter lists are manually typed in the main document; no SEQ, REF, or generated list field codes were found there. Refreshing Word fields alone will not repair these lists.
- A limited arithmetic spot check of Table 37 agrees: PHP 1,500 + PHP 5,000 + PHP 4,000 = PHP 10,500. This audit does not validate all numerical research results or technical claims.

## Verification and scope

Extracted the DOCX body, table contents, caption labels, and field codes. Rendered the unchanged source through Microsoft Word in read-only mode to an internal 223-page PDF; verified page references against that rendering and visually inspected relevant list and finding pages. The usual packaged LibreOffice renderer was unavailable. Printed body pages begin after eight front-matter physical pages. Pagination can change after editing or with a different Word layout environment.

This is a numbering, caption, list-completeness, and page-reference audit. It is not a technical validation of every diagram, screenshot, research statistic, or table value. No edits were applied.

## Complete existing list comparison

| Type | Number | Title | Listed page | Verified printed page | Result |
|---|---|---|---:|---:|---|
| Table | 1 | Comparative Matrix | 33 | 33 | Correct |
| Table | 2 | Comparative Matrix Summary | 36 | 36 | Correct |
| Table | 3 | Agile Sprint Plan | 40 | 40 | Correct |
| Table | 4 | Business Model Canvas | 45 | 45 | Correct |
| Table | 5 | Validation Board | 50 | 50 | Correct |
| Table | 6 | Gantt Chart | 53 | 53 | Correct |
| Table | 7 | User | 112 | 112 | Correct |
| Table | 8 | ConnectionCode | 113 | 113 | Correct |
| Table | 9 | TrustedContactConnection | 113 | 113 | Correct |
| Table | 10 | DriverCalibration | 115 | 115 | Correct |
| Table | 11 | ProvisionedDevice | 116 | 116 | Correct |
| Table | 12 | DriverPreference | 117 | 117 | Correct |
| Table | 13 | MonitoringLogPreference | 118 | 118 | Correct |
| Table | 14 | MonitoringSession | 118 | 118 | Correct |
| Table | 15 | Alert | 119 | 119 | Correct |
| Table | 16 | AlertSign | 120 | 120 | Correct |
| Table | 17 | StageSyncRecord | 120 | 120 | Correct |
| Table | 18 | StageSyncRecordSign | 122 | 122 | Correct |
| Table | 19 | MonitoringLog | 122 | 122 | Correct |
| Table | 20 | MonitoringLogEvent | 123 | 123 | Correct |
| Table | 21 | SharedStage3Record | 125 | 125 | Correct |
| Table | 22 | Device | 126 | 126 | Correct |
| Table | 23 | DeviceOwnership | 127 | 127 | Correct |
| Table | 24 | DeviceDriverAccess | 128 | 128 | Correct |
| Table | 25 | DeviceSubscriptionEvent | 130 | 130 | Correct |
| Table | 26 | DeviceCredential | 131 | 131 | Correct |
| Table | 27 | DeviceAccessGrant | 132 | 132 | Correct |
| Table | 28 | DeviceIdentityMapping | 133 | 133 | Correct |
| Table | 29 | Technology Stack | 136 | 136 | Correct |
| Table | 30 | Software Specification | 137 | 137 | Correct |
| Table | 31 | Hardware Specification | 138 | 138 | Correct |
| Table | 32 | List Of Modules | 140 | 140 | Correct |
| Figure | 1 | Flow of the Study | 11 | 11 | Correct |
| Figure | 2 | Agile Methodology | 38 | 38 | Correct |
| Figure | 3 | Program Workflow (Drivers) | 47 | 47 | Correct |
| Figure | 4 | Program Workflow (Monitoring Session) | 48 | 48 | Correct |
| Figure | 5 | Program Workflow (Trusted Contact) | 49 | 49 | Correct |
| Figure | 6.1 | Functional Decomposition Diagram | 58 | 57 | Change page |
| Figure | 6.2 | Functional Decomposition Diagram cont’d | 58 | 58 | Correct |
| Figure | 6.3 | Functional Decomposition Diagram cont’d | 59 | 59 | Correct |
| Figure | 7.1 | Use Case Diagram | 61 | 61 | Correct |
| Figure | 7.2 | Use Case Diagram cont’d | 62 | 62 | Correct |
| Figure | 8 | Starter Screen (Driver and Trusted Contact) | 64 | 64 | Correct |
| Figure | 9 | Sign In Screen (Driver and Trusted Contact) | 65 | 65 | Correct |
| Figure | 10 | Create Account Screen (Driver and Trusted Contact) | 66 | 66 | Correct |
| Figure | 11 | Forgot Password Screen (Driver and Trusted Contact) | 67 | 67 | Correct |
| Figure | 12 | Setup and Device Screen (Driver) | 68 | 68 | Correct |
| Figure | 13 | Device Connection Screen (Driver) | 69 | 69 | Correct |
| Figure | 14 | Camera Alignment Screen (Driver) | 70 | 70 | Correct |
| Figure | 15 | Driver Calibration Screen (Driver) | 71 | 71 | Correct |
| Figure | 16 | Driver Home Screen (Driver) | 72 | 72 | Correct |
| Figure | 17 | Camera Preview Screen (Driver) | 73 | 73 | Correct |
| Figure | 18 | Before Monitoring Dialog (Driver) | 74 | 74 | Correct |
| Figure | 19 | Monitoring Activity Screen (Driver) | 75 | 75 | Correct |
| Figure | 20 | Alert History Screen (Driver) | 76 | 76 | Correct |
| Figure | 21 | Alert Detail Screen (Driver) | 77 | 77 | Correct |
| Figure | 22 | Trusted Contacts Screen (Driver) | 78 | 78 | Correct |
| Figure | 23 | Invite a Trusted Contact Screen (Driver) | 79 | 79 | Correct |
| Figure | 24 | Shared Alerts Screen (Trusted Contact) | 80 | 80 | Correct |
| Figure | 25 | Invite a Driver Screen (Trusted Contact) | 81 | 81 | Correct |
| Figure | 26 | Requests Screen (Trusted Contact) | 82 | 82 | Correct |
| Figure | 27 | Driver Settings Screen (Driver) | 83 | 83 | Correct |
| Figure | 28 | Subscriptions and Drivers Screen (Driver) | 84 | 84 | Correct |
| Figure | 29 | Invite an Additional Driver Screen (Driver) | 85 | 85 | Correct |
| Figure | 30 | Demo Subscription Activation Dialog (Driver) | 86 | 86 | Correct |
| Figure | 31 | Device Invitation Screen (Driver) | 87 | 87 | Correct |
| Figure | 32 | Active Additional Driver Access Screen (Driver) | 88 | 88 | Correct |
| Figure | 33 | Trusted Contact Settings Screen (Trusted Contact) | 89 | 89 | Correct |
| Figure | 34 | Account Screen (Driver and Trusted Contact) | 90 | 90 | Correct |
| Figure | 35 | History and Storage Screen (Driver and Trusted Contact) | 91 | 91 | Correct |
| Figure | 36 | Monitoring Logs Screen (Driver) | 92 | 92 | Correct |
| Figure | 37 | Session Log Screen (Driver) | 93 | 93 | Correct |
| Figure | 38 | Log Deletion Screen (Driver) | 94 | 94 | Correct |
| Figure | 39 | Driver Notifications Screen (Driver) | 95 | 95 | Correct |
| Figure | 40 | Trusted Contact Notifications Screen (Trusted Contact) | 96 | 96 | Correct |
| Figure | 41 | Warning Sound Screen (Driver) | 97 | 97 | Correct |
| Figure | 42 | About DriveAlert Screen (Driver and Trusted Contact) | 98 | 98 | Correct |
| Figure | 43 | Log Out Confirmation Dialog (Driver and Trusted Contact) | 99 | 99 | Correct |
| Figure | 44 | Sign In Screen (Web) | 100 | 100 | Correct |
| Figure | 45 | Create Account Screen (Web) | 100 | 100 | Correct |
| Figure | 46 | Dashboard Screen (Web) | 101 | 101 | Correct |
| Figure | 47 | Connected Drivers Screen (Web) | 101 | 101 | Correct |
| Figure | 48 | Shared Alert History Screen (Web) | 102 | 102 | Correct |
| Figure | 49 | Shared Alert Detail Screen (Web) | 102 | 102 | Correct |
| Figure | 50 | Requests Screen (Web) | 103 | 103 | Correct |
| Figure | 51 | Invite a Driver Screen (Web) | 103 | 103 | Correct |
| Figure | 52 | Account Screen (Web) | 104 | 104 | Correct |
| Figure | 53 | Settings Screen (Web) | 104 | 104 | Correct |
| Figure | 54 | History and Storage Screen (Web) | 105 | 105 | Correct |
| Figure | 55 | About DriveAlert Screen (Web) | 105 | 105 | Correct |
| Figure | 56 | Driver Storyboard | 106 | 106 | Correct |
| Figure | 57 | Trusted Contact Storyboard | 107 | 107 | Correct |
| Figure | 58 | Trusted Contact Storyboard (Web) | 108 | 108 | Correct |
| Figure | 59 | Entity Relationship Diagram | 109 | 109 | Correct |
| Figure | 60 | DriveAlert Network Model | 134 | 134 | Correct |
| Figure | 61 | DriveAlert Network Topology | 135 | 135 | Correct |
| Figure | 62 | DriveAlert IoT Hardware Architecture | 139 | 139 | Correct |

## Body caption inventory

| Caption | Title | Verified printed page |
|---|---|---:|
| Figure 1 | Flow of the Study | 11 |
| Table 1.1 | COMPARATIVE MATRIX | 33 |
| Table 1.2 | COMPARATIVE MATRIX | 34 |
| Table 1.3 | COMPARATIVE MATRIX | 35 |
| Table 2 | COMPARATIVE MATRIX SUMMARY | 36 |
| Figure 2 | Agile Methodology | 38 |
| Table 3.1 | AGILE SPRINT PLAN | 40 |
| Table 3.2 | AGILE SPRINT PLAN | 41 |
| Table 3.3 | AGILE SPRINT PLAN | 42 |
| Table 3.4 | AGILE SPRINT PLAN | 43 |
| Table 3.5 | AGILE SPRINT PLAN | 44 |
| Table 4 | BUSINESS MODEL CANVAS | 45 |
| Figure 3 | Program Workflow (Drivers) | 47 |
| Figure 4 | Program Workflow (Monitoring Session) | 48 |
| Figure 5 | Program Workflow (Trusted Contact) | 49 |
| Table 5.1 | VALIDATION BOARD | 50 |
| Table 5.2 | VALIDATION BOARD cont’d | 51 |
| Table 6.1 | GANTT CHART | 53 |
| Table 6.2 | GANTT CHART | 54 |
| Table 6.3 | GANTT CHART | 55 |
| Table 6.4 | GANTT CHART | 56 |
| Table 6.5 | GANTT CHART | 57 |
| Figure 6.1 | Functional Decomposition Diagram | 57 |
| Figure 6.2 | Functional Decomposition Diagram cont’d | 58 |
| Figure 6.3 | Functional Decomposition Diagram cont’d | 59 |
| Figure 7.1 | Use Case Diagram | 61 |
| Figure 7.2 | Use Case Diagram cont’d | 62 |
| Figure 8 | Starter Screen (Driver and Trusted Contact) | 64 |
| Figure 9 | Sign In Screen (Driver and Trusted Contact) | 65 |
| Figure 10 | Create Account Screen (Driver and Trusted Contact) | 66 |
| Figure 11 | Forgot Password Screen (Driver and Trusted Contact) | 67 |
| Figure 12 | Setup and Device Screen (Driver) | 68 |
| Figure 13 | Device Connection Screen (Driver) | 69 |
| Figure 14 | Camera Alignment Screen (Driver) | 70 |
| Figure 15 | Driver Calibration Screen (Driver) | 71 |
| Figure 16 | Driver Home Screen (Driver) | 72 |
| Figure 17 | Camera Preview Screen (Driver) | 73 |
| Figure 18 | Before Monitoring Dialog (Driver) | 74 |
| Figure 19 | Monitoring Activity Screen (Driver) | 75 |
| Figure 20 | Alert History Screen (Driver) | 76 |
| Figure 21 | Alert Detail Screen (Driver) | 77 |
| Figure 22 | Trusted Contacts Screen (Driver) | 78 |
| Figure 23 | Invite a Trusted Contact Screen (Driver) | 79 |
| Figure 24 | Shared Alerts Screen (Trusted Contact) | 80 |
| Figure 25 | Invite a Driver Screen (Trusted Contact) | 81 |
| Figure 26 | Requests Screen (Trusted Contact) | 82 |
| Figure 27 | Driver Settings Screen (Driver) | 83 |
| Figure 28 | Subscriptions and Drivers Screen (Driver) | 84 |
| Figure 29 | Invite an Additional Driver Screen (Driver) | 85 |
| Figure 30 | Demo Subscription Activation Dialog (Driver) | 86 |
| Figure 31 | Device Invitation Screen (Driver) | 87 |
| Figure 32 | Active Additional Driver Access Screen (Driver) | 88 |
| Figure 33 | Trusted Contact Settings Screen (Trusted Contact) | 89 |
| Figure 34 | Account Screen (Driver and Trusted Contact) | 90 |
| Figure 35 | History and Storage Screen (Driver and Trusted Contact) | 91 |
| Figure 36 | Monitoring Logs Screen (Driver) | 92 |
| Figure 37 | Session Log Screen (Driver) | 93 |
| Figure 38 | Log Deletion Screen (Driver) | 94 |
| Figure 39 | Driver Notifications Screen (Driver) | 95 |
| Figure 40 | Trusted Contact Notifications Screen (Trusted Contact) | 96 |
| Figure 41 | Warning Sound Screen (Driver) | 97 |
| Figure 42 | About DriveAlert Screen (Driver and Trusted Contact) | 98 |
| Figure 43 | Log Out Confirmation Dialog (Driver and Trusted Contact) | 99 |
| Figure 44 | Sign In Screen (Web) | 100 |
| Figure 45 | Create Account Screen (Web) | 100 |
| Figure 46 | Dashboard Screen (Web) | 101 |
| Figure 47 | Connected Drivers Screen (Web) | 101 |
| Figure 48 | Shared Alert History Screen (Web) | 102 |
| Figure 49 | Shared Alert Detail Screen (Web) | 102 |
| Figure 50 | Requests Screen (Web) | 103 |
| Figure 51 | Invite a Driver Screen (Web) | 103 |
| Figure 52 | Account Screen (Web) | 104 |
| Figure 53 | Settings Screen (Web) | 104 |
| Figure 54 | History and Storage Screen (Web) | 105 |
| Figure 55 | About DriveAlert Screen (Web) | 105 |
| Figure 56 | Driver Storyboard | 106 |
| Figure 57 | Trusted Contact Storyboard | 107 |
| Figure 58 | Trusted Contact Storyboard (Web) | 108 |
| Figure 59 | Entity Relationship Diagram | 109 |
| Table 7 | User | 112 |
| Table 8 | ConnectionCode | 113 |
| Table 9 | TrustedContactConnection | 113 |
| Table 9.1 | TrustedContactConnection cont’d | 114 |
| Table 10 | DriverCalibration | 115 |
| Table 11 | ProvisionedDevice | 116 |
| Table 12 | DriverPreference | 117 |
| Table 13 | MonitoringLogPreference | 118 |
| Table 14 | MonitoringSession | 118 |
| Table 14.1 | MonitoringSession cont’d | 119 |
| Table 15 | Alert | 119 |
| Table 16 | AlertSign | 120 |
| Table 17 | StageSyncRecord | 120 |
| Table 17.1 | StageSyncRecord cont’d | 121 |
| Table 18 | StageSyncRecordSign | 122 |
| Table 19 | MonitoringLog | 122 |
| Table 20 | MonitoringLogEvent | 123 |
| Table 20.1 | MonitoringLogEvent cont’d | 124 |
| Table 21 | SharedStage3Record | 125 |
| Table 22 | Device | 126 |
| Table 23 | DeviceOwnership | 127 |
| Table 24 | DeviceDriverAccess | 128 |
| Table 24.1 | DeviceDriverAccess cont’d | 129 |
| Table 25 | DeviceSubscriptionEvent | 130 |
| Table 26 | DeviceCredential | 131 |
| Table 27 | DeviceAccessGrant | 132 |
| Table 28 | DeviceIdentityMapping | 133 |
| Figure 60 | DriveAlert Network Model | 134 |
| Figure 61 | DriveAlert Network Topology | 135 |
| Table 29 | TECHNOLOGY STACK | 136 |
| Table 30 | SOFTWARE SPECIFICATION | 137 |
| Table 31 | HARDWARE SPECIFICATION | 138 |
| Figure 62 | DriveAlert IoT Hardware Architecture | 139 |
| Table 32.1 | LIST OF MODULES | 140 |
| Table 32.2 | LIST OF MODULES cont’d | 141 |
| Table 33 | UNIT TESTING | 142 |
| Table 33.1 | UNIT TESTING cont’d | 143 |
| Table 33.2 | UNIT TESTING cont’d | 144 |
| Table 33.3 | UNIT TESTING cont’d | 145 |
| Table 33.4 | UNIT TESTING cont’d | 146 |
| Table 33.5 | UNIT TESTING cont’d | 147 |
| Table 33.6 | UNIT TESTING cont’d | 148 |
| Table 33.7 | UNIT TESTING cont’d | 149 |
| Table 34 | INTEGRATION TESTING | 150 |
| Table 34.1 | INTEGRATION TESTING cont’d | 151 |
| Table 35 | ALPHA TESTING | 152 |
| Table 36 | ACCEPTANCE TESTING | 153 |
| Figure 63 | DriveAlert Deployment Diagram | 155 |
| Table 37 | COST SPECIFICATION | 156 |
| Table 38 | HUMAN SPECIFICATION | 159 |
| Figure 64 | Project Road Map | 178 |

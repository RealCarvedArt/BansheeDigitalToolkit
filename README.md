# Banshee Digital Toolkit for Android

A native, private-by-design Android conversion of `BansheeDigitalToolkit_v2.10.xlsx`
for Yamaha Banshee owners and builders.

The app preserves the workbook's 22 sheets, 7,242 populated cells, 680 formulas, and
61 embedded illustrations while replacing spreadsheet navigation with fast, touch-friendly
tools. It runs entirely offline, contains no advertising or analytics, and requests no
dangerous permissions.

## Android features

- Interactive jetting calculator driven by the workbook's full calculation matrix
- VIN decoder with 17-position breakdown and check-digit validation
- Six-gear speed calculator with live RPM table
- Horsepower, pre-mix, chain, displacement, and port angle-area calculators
- Engine Formula Lab containing all 90 workbook formulas
- Searchable jetting, maintenance, torque, troubleshooting, and reference material
- Original color guides, carburetor diagrams, wiring schematic, and conversion illustrations
- Responsive dark field-console UI with live validation and Android Back support
- Lazy-loaded workbook sheets for a quick startup and modest memory use

The complete migration map is documented in
[`docs/MIGRATION_MAP.md`](docs/MIGRATION_MAP.md). The source workbook audit is in
[`docs/WORKBOOK_AUDIT.md`](docs/WORKBOOK_AUDIT.md).

## Security and privacy

The manifest declares no internet, storage, location, camera, microphone, contacts, or
advertising permissions. Cleartext traffic and Android backups are disabled. Workbook data
is packaged as read-only application assets; inputs stay in process and are not persisted or
transmitted.

See [`docs/SECURITY.md`](docs/SECURITY.md) for the threat model and release guidance.

## Verification

The unit suite evaluates every one of the workbook's 680 formula cells and compares the
result with Excel's cached result. It also tests changed inputs and pins the workbook SHA-256:

`5e1159a637d5227cc41898c2ea5c6c60174892dbd0de7d994e7876e2e3d120b9`

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

For validation details, see [`docs/VERIFICATION.md`](docs/VERIFICATION.md).

## Build requirements

- Android Studio / Android SDK 37
- JDK 17
- No network connection is needed after Gradle dependencies are cached

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

A production release must be signed with a private release key that is never committed to
the repository.

## Re-extracting a workbook revision

The checked-in Android assets are deterministic output from the audited workbook. To migrate
a new workbook revision, install `openpyxl==3.1.5`, then run:

```powershell
python tools\extract_workbook.py "BansheeDigitalToolkit_v2.10.xlsx" `
  --output app\src\main\assets\workbook.json `
  --media-dir app\src\main\assets\workbook_media `
  --audit-markdown docs\WORKBOOK_AUDIT.md
```

Run the full verification command afterward. A changed source hash or formula count causes
the integrity test to fail until the migration is intentionally reviewed.

## Project status

`0.1.0-alpha01` is an evaluation build. Calculator output is baseline guidance, not a
substitute for Yamaha service information, component manufacturer specifications, plug
reading, detonation monitoring, or qualified mechanical judgment.

## Support

Created by [CarvedArt](https://ko-fi.com/carvedart). The software and its maintenance
guidance are provided as-is, without warranties or guarantees of suitability.

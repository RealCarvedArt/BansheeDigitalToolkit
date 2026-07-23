# Security and privacy

## Security posture

The app is intentionally offline and single-process:

- No `INTERNET` permission
- No storage, media, location, camera, microphone, contacts, or notification permissions
- No analytics, advertising SDK, account system, WebView, or remote content
- Cleartext network traffic disabled
- Android cloud/device backup disabled, including data-extraction rules
- No exported component except the required launcher activity
- No secrets, credentials, keystores, or user-entered values committed to the repository
- Release resource shrinking and code optimization enabled

Workbook content is bundled under `app/src/main/assets` and opened read-only. Calculator
inputs are held only in memory and are discarded with the activity/process. The app does not
collect or transmit personal data.

## Threat model

The primary risks are calculation integrity, malicious workbook revisions, dependency
compromise, and distribution tampering.

- **Calculation integrity:** all 680 formula cells are compared to Excel's cached outputs.
- **Workbook revision:** the source name, counts, and SHA-256 are pinned by tests.
- **Parser exposure:** the JSON parser reads only packaged assets, not arbitrary external files.
- **Dependency surface:** the app uses AndroidX UI libraries only; the formula and JSON engines
  are local and dependency-free.
- **Distribution:** published APKs should include a SHA-256 checksum and be served from the
  repository's GitHub Releases page.

## Production signing

The evaluation APK is debug-signed and must not be promoted as a Play production artifact.
For production:

1. Create a dedicated signing key outside the repository.
2. Inject signing credentials through a protected CI secret store or local untracked file.
3. Build the minified release variant.
4. Verify the signing certificate and APK checksum.
5. Preserve the signing key securely for all future upgrades.

Never commit a `.jks`, `.keystore`, password, token, or generated signing property file.

## Safety boundary

Jetting and engine calculations can affect reliability and operator safety. Results are
presented as baselines with an in-app warning to verify plug color, detonation margin, fuel
quality, temperature, and manufacturer specifications before sustained high-load use.

Please report security issues privately to the repository owner rather than disclosing
exploitable details in a public issue.

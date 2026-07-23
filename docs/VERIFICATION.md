# Verification record

## Automated checks

`FormulaParityTest` provides three independent gates:

1. Evaluate every formula in all 22 sheets and compare all 680 results with the cached Excel
   values using a scale-aware numeric tolerance.
2. Change calculator inputs and confirm the pre-mix and displacement sheets recalculate.
3. Pin the workbook's 22-sheet, 680-formula, 61-media contract and SHA-256.

The release gate is:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug assembleRelease --no-parallel
```

## Device checks

The debug build is exercised on an Android emulator for:

- Cold launch and lazy workbook loading
- Dashboard search and category filtering
- Jetting defaults and live modification response
- Pre-mix defaults: 32:1 and 5 gallons produce 20 oz / 591.4 cc
- Full-resolution electrical schematic loading
- Toolbar and Android system Back navigation
- Absence of application crashes and ANRs in the runtime log

## Release checklist

- [ ] Lint completes without errors
- [ ] Unit tests pass, including 680/680 formula parity
- [ ] Debug and minified release variants assemble
- [ ] APK signing certificate is appropriate for the intended channel
- [ ] SHA-256 checksum is published beside the APK
- [ ] Git tag matches the Android `versionName`

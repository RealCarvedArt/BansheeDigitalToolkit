# Workbook-to-Android migration map

Every source worksheet is represented in the Android app. Purpose-built screens are used
where the workbook accepts inputs; content-heavy sheets use a native reference renderer that
retains tables, formulas, text, and embedded media.

| Workbook sheet | Android destination | Migration behavior |
|---|---|---|
| ReadMe | About the Toolkit | Native reference page |
| How to Jet a Banshee | How to Jet | Step-by-step reference |
| Banshee Jetting Calc | Jetting Calculator | Purpose-built inputs and live workbook formula engine |
| Jet Sample Settings | Sample Jetting | Native reference table |
| Needle Jet Specs | Needle Jet Specs | Native data and original illustration |
| How to Sync Carbs | Sync Carbs | Illustrated native procedure |
| Quick Specs | Quick Specs | Native reference data |
| Torque Specs | Torque Specs | Native table |
| Color Options | Colors by Year | Year data and 30 original images |
| VIN Decoder | VIN Decoder | Purpose-built decoder, check digit, and model-year lookup |
| Speed Calc | Speed Calculator | Purpose-built drivetrain inputs and six-gear RPM results |
| HP Calc | Horsepower Calculator | Live quarter-mile calculations |
| Pre-Mix Ratio Calc | Pre-Mix Calculator | Live ounces and cubic-centimeter results |
| Chain Calc | Chain Calculator | Live chain-length calculation |
| Displacement Calc | Displacement Calculator | Live cylinder, bore, and stroke calculation |
| Engine Building Formulas | Engine Formula Lab | All 90 formulas with editable inputs |
| Angle Area | Port Angle Area | Native chart and source values |
| Mikuni Troubleshooting | Mikuni Troubleshooting | Symptoms, causes, and corrections |
| Schematic | Electrical Schematic | Full-resolution original wiring diagram |
| Banshee Jetting FAQ | Jetting FAQ | Native text and six original images |
| Carburetor Theory 101 | Carburetor Theory 101 | Native theory guide and nine original images |
| AC to DC Conversion | AC to DC Conversion | Four-page illustrated procedure |

## Runtime contract

`tools/extract_workbook.py` reads workbook values, cached formula results, formulas, hidden
rows/columns, and media placements. It emits a small `workbook.json` index plus one JSON file
per sheet. The repository loads only the selected sheet and caches it for the rest of the
process lifetime.

The formula engine implements every function used by the audited workbook: `ABS`, `ACOS`,
`AND`, `AVERAGE`, `CEILING`, `COS`, `EVEN`, `IF`, `ISNUMBER`, `MAX`, `MOD`, `OR`, `PI`,
`ROUND`, `ROUNDDOWN`, `ROUNDUP`, `SIN`, `SQRT`, `SUM`, and `VALUE`, together with workbook
operators and ranges.

Workbook calculation defaults are the behavioral contract. Custom screens improve input
ergonomics but do not replace that contract with unrelated formulas.

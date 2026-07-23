package art.carved.bansheetoolkit.ui

enum class ToolMode {
    JETTING,
    VIN,
    SPEED,
    SIMPLE_CALCULATOR,
    ENGINE_FORMULAS,
    ANGLE_CHART,
    REFERENCE,
}

data class ToolSpec(
    val sheet: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val glyph: String,
    val mode: ToolMode,
)

object ToolCatalog {
    val tools = listOf(
        ToolSpec("Banshee Jetting Calc", "Jetting Calculator", "Build a baseline from mods, altitude, temperature, humidity, and premix", "Jetting", "JET", ToolMode.JETTING),
        ToolSpec("How to Jet a Banshee", "How to Jet", "Complete step-by-step jetting workflow", "Jetting", "HOW", ToolMode.REFERENCE),
        ToolSpec("Jet Sample Settings", "Sample Jetting", "Searchable real-world combinations plus °C/°F conversion", "Jetting", "SET", ToolMode.REFERENCE),
        ToolSpec("Needle Jet Specs", "Needle Jet Specs", "Needle dimensions, clip positions, and reference diagram", "Jetting", "NDL", ToolMode.REFERENCE),
        ToolSpec("How to Sync Carbs", "Sync Carbs", "Illustrated carburetor synchronization procedure", "Jetting", "SYN", ToolMode.REFERENCE),
        ToolSpec("VIN Decoder", "VIN Decoder", "Decode all 17 positions, validate the check digit, and identify model year", "Calculators", "VIN", ToolMode.VIN),
        ToolSpec("Speed Calc", "Speed Calculator", "Six-gear MPH table from drivetrain and tire dimensions", "Calculators", "MPH", ToolMode.SPEED),
        ToolSpec("HP Calc", "Horsepower Calculator", "Rear-wheel and estimated crank horsepower from quarter-mile data", "Calculators", "HP", ToolMode.SIMPLE_CALCULATOR),
        ToolSpec("Pre-Mix Ratio Calc", "Pre-Mix Calculator", "Oil ounces and cubic centimeters for any gas volume and ratio", "Calculators", "MIX", ToolMode.SIMPLE_CALCULATOR),
        ToolSpec("Chain Calc", "Chain Calculator", "Required chain length after swingarm extension", "Calculators", "CHN", ToolMode.SIMPLE_CALCULATOR),
        ToolSpec("Displacement Calc", "Displacement Calculator", "Engine displacement from cylinders, bore, and stroker offset", "Calculators", "CC", ToolMode.SIMPLE_CALCULATOR),
        ToolSpec("Engine Building Formulas", "Engine Formula Lab", "All 90 live engine, conversion, electrical, and performance formulas", "Calculators", "FX", ToolMode.ENGINE_FORMULAS),
        ToolSpec("Angle Area", "Port Angle Area", "Visual exhaust and transfer-port angle-area reference", "Calculators", "ANG", ToolMode.ANGLE_CHART),
        ToolSpec("Quick Specs", "Quick Specs", "Core Yamaha Banshee dimensions, capacities, and setup data", "Specs", "QCK", ToolMode.REFERENCE),
        ToolSpec("Torque Specs", "Torque Specs", "Searchable fastener torque table", "Specs", "TQ", ToolMode.REFERENCE),
        ToolSpec("Color Options", "Colors by Year", "Complete year-by-year visual color reference", "Specs", "CLR", ToolMode.REFERENCE),
        ToolSpec("Mikuni Troubleshooting", "Mikuni Troubleshooting", "Symptoms, causes, and corrective actions", "Learn", "FIX", ToolMode.REFERENCE),
        ToolSpec("Banshee Jetting FAQ", "Jetting FAQ", "Detailed answers and illustrated tuning reference", "Learn", "FAQ", ToolMode.REFERENCE),
        ToolSpec("Carburetor Theory 101", "Carburetor Theory 101", "Illustrated carb circuits and operating theory", "Learn", "101", ToolMode.REFERENCE),
        ToolSpec("Schematic", "Electrical Schematic", "Full-resolution wiring diagram", "Electrical", "SCH", ToolMode.REFERENCE),
        ToolSpec("AC to DC Conversion", "AC to DC Conversion", "Four-page illustrated conversion procedure", "Electrical", "DC", ToolMode.REFERENCE),
        ToolSpec("ReadMe", "About the Toolkit", "Workbook provenance, author notes, and original usage information", "About", "i", ToolMode.REFERENCE),
    )

    val categories = listOf("All", "Jetting", "Calculators", "Specs", "Learn", "Electrical", "About")
}

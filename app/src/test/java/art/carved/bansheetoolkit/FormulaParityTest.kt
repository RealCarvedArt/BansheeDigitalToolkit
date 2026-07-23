package art.carved.bansheetoolkit

import art.carved.bansheetoolkit.data.WorkbookRepository
import art.carved.bansheetoolkit.formula.ExcelValue
import art.carved.bansheetoolkit.formula.FormulaEngine
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class FormulaParityTest {
    @Test
    fun everyWorkbookFormulaMatchesItsExcelCachedResult() {
        var checked = 0
        val failures = mutableListOf<String>()
        for (sheet in workbook.sheets) {
            val engine = FormulaEngine(sheet)
            for (cell in sheet.cells.values.filter { it.formula != null }) {
                val expected = cell.cached
                val actual = engine.value(cell.coordinate)
                val failure = compare(expected, actual)
                if (failure != null) {
                    failures += "${sheet.name}!${cell.coordinate}: $failure; ${cell.formula}"
                }
                checked++
            }
        }
        assertEquals(680, checked)
        assertTrue(failures.take(20).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun inputsRecalculateWithoutMutatingTheWorkbookContract() {
        val premix = workbook.sheet("Pre-Mix Ratio Calc")
        val engine = FormulaEngine(premix)
        engine.set("D4", 40)
        engine.set("D5", 5)
        assertEquals("16", engine.value("D7").display())
        assertEquals("473.12", engine.value("D8").display())

        val displacement = FormulaEngine(workbook.sheet("Displacement Calc"))
        displacement.set("C4", 2)
        displacement.set("C5", 64)
        displacement.set("C6", 4)
        assertEquals("373.171", displacement.value("C8").display())
    }

    @Test
    fun manifestIntegrityMetadataIsPinned() {
        assertEquals(22, workbook.source.sheetCount)
        assertEquals(680, workbook.source.formulaCount)
        assertEquals(61, workbook.source.mediaCount)
        assertEquals(
            "5e1159a637d5227cc41898c2ea5c6c60174892dbd0de7d994e7876e2e3d120b9",
            workbook.source.sha256,
        )
        assertFalse(workbook.sheets.any { it.name.isBlank() })
    }

    private fun compare(expected: Any?, actual: ExcelValue): String? {
        if (actual is ExcelValue.ErrorValue) return "engine error ${actual.message}"
        if (expected == null) {
            return if (actual.display().isEmpty()) null else "expected blank, got ${actual.display()}"
        }
        if (expected is Number) {
            val actualNumber = (actual as? ExcelValue.NumberValue)?.number
                ?: actual.display().toDoubleOrNull()
                ?: return "expected number $expected, got ${actual.display()}"
            val expectedNumber = expected.toDouble()
            val tolerance = max(1e-9, abs(expectedNumber) * 1e-10)
            return if (abs(expectedNumber - actualNumber) <= tolerance) null
            else "expected $expectedNumber, got $actualNumber"
        }
        val expectedText = expected.toString()
        return if (expectedText == actual.display()) null
        else "expected '$expectedText', got '${actual.display()}'"
    }

    companion object {
        private lateinit var workbook: WorkbookRepository

        @JvmStatic
        @BeforeClass
        fun loadWorkbook() {
            val manifest = File("src/main/assets/workbook.json")
            require(manifest.isFile) { "Missing workbook manifest at ${manifest.absolutePath}" }
            workbook = WorkbookRepository.fromJson(manifest.readText(Charsets.UTF_8)) { filename ->
                File("src/main/assets/workbook_sheets", filename).readText(Charsets.UTF_8)
            }
        }
    }
}

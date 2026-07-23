package art.carved.bansheetoolkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import art.carved.bansheetoolkit.data.WorkbookCell
import art.carved.bansheetoolkit.data.WorkbookRepository
import art.carved.bansheetoolkit.data.WorkbookRow
import art.carved.bansheetoolkit.data.WorkbookSheet
import art.carved.bansheetoolkit.formula.ExcelValue
import art.carved.bansheetoolkit.formula.FormulaEngine
import art.carved.bansheetoolkit.ui.ToolCatalog
import art.carved.bansheetoolkit.ui.ToolMode
import art.carved.bansheetoolkit.ui.ToolSpec
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import kotlin.math.max

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var content: FrameLayout
    private lateinit var workbook: WorkbookRepository
    private var currentTool: ToolSpec? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.banshee_black))
        }
        toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            setTitleTextColor(color(R.color.banshee_white))
            setBackgroundColor(color(R.color.banshee_black))
            elevation = 0f
        }
        content = FrameLayout(this)
        root.addView(toolbar, linearParams(match, 64.dp))
        root.addView(content, linearParams(match, 0, 1f))
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentTool != null) {
                        showHome()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        showLoading()
        Thread {
            val loaded = assets.open("workbook.json").use { index ->
                WorkbookRepository.load(index) { filename ->
                    assets.open("workbook_sheets/$filename")
                }
            }
            runOnUiThread {
                workbook = loaded
                showHome()
            }
        }.start()
    }

    private fun showLoading() {
        content.removeAllViews()
        content.addView(
            LinearLayout(this).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.VERTICAL
                addView(ProgressBar(this@MainActivity))
                addView(bodyText("Reading the original workbook contract…").apply {
                    gravity = Gravity.CENTER
                    setPadding(0, 18.dp, 0, 0)
                })
            },
            frameParams(match, match),
        )
    }

    private fun showHome() {
        currentTool = null
        toolbar.title = getString(R.string.app_name)
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener(null)
        content.removeAllViews()

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 8.dp, 16.dp, 0)
        }
        page.addView(heroCard())

        val searchLayout = TextInputLayout(this).apply {
            hint = getString(R.string.search_tools)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxStrokeColorStateList(ColorStateList.valueOf(color(R.color.banshee_cyan)))
            layoutParams = linearParams(match, wrap).withMargins(top = 14.dp)
        }
        val search = TextInputEditText(searchLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        searchLayout.addView(search)
        page.addView(searchLayout)

        val chips = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = 8.dp
        }
        val chipScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        page.addView(chipScroller, linearParams(match, wrap).withMargins(top = 8.dp))

        val recycler = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2)
            setPadding(0, 8.dp, 0, 16.dp)
            clipToPadding = false
        }
        val adapter = ToolAdapter { showTool(it) }
        recycler.adapter = adapter
        page.addView(recycler, linearParams(match, 0, 1f))
        content.addView(page, frameParams(match, match))

        var category = "All"
        var query = ""
        fun filter() {
            val needle = query.trim().lowercase(Locale.US)
            val results = ToolCatalog.tools.filter { tool ->
                val categoryMatch = category == "All" || tool.category == category
                val metadataMatch = needle.isEmpty() ||
                    listOf(tool.title, tool.subtitle, tool.category, tool.sheet)
                        .any { it.lowercase(Locale.US).contains(needle) }
                categoryMatch && metadataMatch
            }
            adapter.submit(results)
        }

        ToolCatalog.categories.forEachIndexed { index, label ->
            chips.addView(Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = index == 0
                setOnClickListener {
                    category = label
                    filter()
                }
            })
        }
        search.addTextChangedListener(afterTextChanged {
            query = it
            filter()
        })
        filter()
    }

    private fun heroCard(): MaterialCardView = card().apply {
        strokeColor = color(R.color.banshee_cyan_dark)
        addView(
            LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20.dp, 20.dp, 20.dp, 20.dp)
                addView(TextView(context).apply {
                    text = "BANSHEE // FIELD CONSOLE"
                    setTextColor(color(R.color.banshee_cyan))
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = .12f
                })
                addView(TextView(context).apply {
                    text = "Tune smarter. Wrench with confidence."
                    setTextColor(color(R.color.banshee_white))
                    textSize = 25f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, 7.dp, 0, 5.dp)
                })
                addView(bodyText("Every calculator, table, guide, and diagram from workbook v2.10—rebuilt as a fast native toolkit."))
                addView(TextView(context).apply {
                    text = getString(R.string.offline_badge)
                    setTextColor(color(R.color.banshee_amber))
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    letterSpacing = .08f
                    setPadding(0, 14.dp, 0, 0)
                })
            },
        )
    }

    private fun showTool(tool: ToolSpec) {
        currentTool = tool
        toolbar.title = tool.title
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { showHome() }
        showLoading()
        Thread {
            val sheet = workbook.sheet(tool.sheet)
            runOnUiThread {
                if (currentTool != tool) return@runOnUiThread
                content.removeAllViews()
                val view = when (tool.mode) {
                    ToolMode.JETTING -> jettingView(sheet)
                    ToolMode.VIN -> vinView(sheet)
                    ToolMode.SPEED -> speedView(sheet)
                    ToolMode.SIMPLE_CALCULATOR -> simpleCalculatorView(tool, sheet)
                    ToolMode.ENGINE_FORMULAS -> engineFormulaView(sheet)
                    ToolMode.ANGLE_CHART -> angleAreaView(sheet)
                    ToolMode.REFERENCE -> referenceView(tool, sheet)
                }
                content.addView(view, frameParams(match, match))
            }
        }.start()
    }

    private fun toolPage(subtitle: String): Pair<ScrollView, LinearLayout> {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 8.dp, 16.dp, 32.dp)
            addView(bodyText(subtitle).apply {
                setPadding(2.dp, 0, 2.dp, 14.dp)
            })
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(body)
        }
        return scroll to body
    }

    private fun simpleCalculatorView(tool: ToolSpec, sheet: WorkbookSheet): View {
        val engine = FormulaEngine(sheet)
        val config = when (sheet.name) {
            "HP Calc" -> SimpleConfig(listOf("D4", "D5"), listOf("D7", "D8"))
            "Pre-Mix Ratio Calc" -> SimpleConfig(listOf("D4", "D5"), listOf("D7", "D8"))
            "Chain Calc" -> SimpleConfig(listOf("C4", "C5"), listOf("C7", "C8"))
            "Displacement Calc" -> SimpleConfig(listOf("C4", "C5", "C6"), listOf("C8"))
            else -> error("No calculator configuration for ${sheet.name}")
        }
        val (scroll, body) = toolPage(tool.subtitle)
        body.addView(sectionLabel("INPUTS"))
        val outputViews = linkedMapOf<String, TextView>()
        lateinit var refresh: () -> Unit

        config.inputs.forEach { coordinate ->
            val cell = requireNotNull(sheet.cell(coordinate))
            body.addView(numberInput(rowLabel(sheet, cell), cell.shownValue) { value ->
                engine.set(coordinate, value)
                refresh()
            })
        }
        body.addView(sectionLabel("LIVE RESULTS"))
        config.outputs.forEach { coordinate ->
            val cell = requireNotNull(sheet.cell(coordinate))
            val value = metricCard(
                rowLabel(sheet, cell).ifBlank { coordinate },
                engine.value(coordinate).display(),
                rowUnit(sheet, cell),
            )
            outputViews[coordinate] = value.second
            body.addView(value.first)
        }
        refresh = {
            outputViews.forEach { (coordinate, view) ->
                view.text = engine.value(coordinate).display()
            }
        }
        body.addView(safetyNote())
        refresh()
        return scroll
    }

    private fun jettingView(sheet: WorkbookSheet): View {
        val engine = FormulaEngine(sheet)
        val (scroll, body) = toolPage("Interactive parity implementation of the workbook's full jetting matrix.")
        val resultCard = card()
        val resultBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 18.dp, 18.dp, 18.dp)
            addView(sectionLabel("SUGGESTED BASELINE"))
        }
        val main = resultMetric(resultBody, "MAIN JET")
        val pilot = resultMetric(resultBody, "PILOT JET")
        val needle = resultMetric(resultBody, "NEEDLE CLIP")
        val target = bodyText("").apply {
            setTextColor(color(R.color.banshee_amber))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 12.dp, 0, 0)
        }
        resultBody.addView(target)
        resultCard.addView(resultBody)
        body.addView(resultCard)

        lateinit var refresh: () -> Unit
        body.addView(sectionLabel("CURRENT JETS"))
        listOf(
            Triple("C78", "Current main jet", 220),
            Triple("E78", "Current pilot jet", 25),
            Triple("G78", "Current needle clip", 3),
        ).forEach { (coordinate, label, fallback) ->
            body.addView(numberInput(label, sheet.cell(coordinate)?.shownValue ?: fallback) {
                engine.set(coordinate, it)
                refresh()
            })
        }

        val groups = listOf(
            Triple("AIR FILTER • WITH AIRBOX", 4, 8),
            Triple("AIR FILTER • NO AIRBOX", 10, 13),
            Triple("THROTTLE OVERRIDE", 15, 15),
            Triple("PIPES", 17, 20),
            Triple("REEDS", 22, 24),
            Triple("ELECTRICAL", 26, 29),
            Triple("COMPRESSION", 31, 32),
            Triple("MISCELLANEOUS", 34, 42),
            Triple("ELEVATION", 44, 58),
            Triple("TEMPERATURE", 60, 66),
            Triple("HUMIDITY", 68, 70),
            Triple("PREMIX RATIO", 72, 76),
        )
        groups.forEach { (title, first, last) ->
            body.addView(sectionLabel(title))
            for (row in first..last) {
                val label = sheet.cell("A$row")?.shownValue?.toString() ?: continue
                val optionCard = card().apply {
                    layoutParams = linearParams(match, wrap).withMargins(bottom = 8.dp)
                }
                val optionBody = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12.dp, 8.dp, 12.dp, 8.dp)
                }
                val check = MaterialCheckBox(this).apply {
                    text = label
                    setTextColor(color(R.color.banshee_white))
                    isChecked = sheet.cell("B$row")?.shownValue?.toString() == "YES"
                }
                optionBody.addView(check)
                val adjustment = sheet.cell("I$row")?.shownValue?.toString().orEmpty()
                val note = sheet.cell("J$row")?.shownValue?.toString().orEmpty()
                if (adjustment.isNotBlank()) optionBody.addView(captionText(adjustment))
                if (note.isNotBlank()) optionBody.addView(captionText("Note: $note"))
                optionCard.addView(optionBody)
                body.addView(optionCard)
                check.setOnCheckedChangeListener { _, checked ->
                    engine.set("B$row", if (checked) "YES" else "\u00a0")
                    refresh()
                }
            }
        }

        body.addView(sectionLabel("CONDITION CHECKS"))
        val advisory = bodyText("").apply {
            setTextColor(color(R.color.banshee_amber))
            setPadding(2.dp, 8.dp, 2.dp, 12.dp)
        }
        listOf(
            Triple("C86", "Starting elevation (ft ASL)", 2500),
            Triple("C87", "Ending elevation (ft ASL)", 0),
            Triple("C91", "Current temperature (°F)", 107),
        ).forEach { (coordinate, label, fallback) ->
            body.addView(numberInput(label, sheet.cell(coordinate)?.shownValue ?: fallback) {
                engine.set(coordinate, it)
                refresh()
            })
        }
        body.addView(advisory)
        body.addView(safetyNote())

        refresh = {
            main.text = "${engine.value("C83").display()}–${engine.value("D83").display()}"
            pilot.text = "${engine.value("E83").display()}–${engine.value("F83").display()}"
            needle.text = "${engine.value("G83").display()}–${engine.value("H83").display()}"
            target.text = "Workbook targets: main ${engine.value("C84").display()} • pilot ${engine.value("E84").display()} • clip ${engine.value("G84").display()}"
            advisory.text = "Elevation: ${engine.value("F87").display()} ft — ${engine.value("E88").display()}\nTemperature: ${engine.value("D91").display()} ${engine.value("E91").display()}"
        }
        refresh()
        return scroll
    }

    private fun vinView(sheet: WorkbookSheet): View {
        val engine = FormulaEngine(sheet)
        val (scroll, body) = toolPage("Enter a 17-character VIN to decode each position and verify its ISO check digit locally.")
        val inputLayout = TextInputLayout(this).apply {
            hint = "17-digit VIN"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = linearParams(match, wrap).withMargins(bottom = 12.dp)
        }
        val input = TextInputEditText(inputLayout.context).apply {
            filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(17))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            letterSpacing = .08f
            setText(engine.value("E24").display())
            setSelection(text?.length ?: 0)
        }
        inputLayout.addView(input)
        body.addView(inputLayout)

        val status = card()
        val statusText = TextView(this).apply {
            setPadding(18.dp, 18.dp, 18.dp, 18.dp)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        }
        status.addView(statusText)
        body.addView(status)
        body.addView(sectionLabel("DECODED POSITIONS"))
        val outputs = linkedMapOf<String, TextView>()
        for (row in 4..14) {
            val label = sheet.cell("D$row")?.shownValue?.toString() ?: continue
            val pair = metricCard(label, engine.value("E$row").display(), "")
            outputs["E$row"] = pair.second
            body.addView(pair.first)
        }
        val extra = listOf(
            "F21" to "Calculated check digit",
            "E23" to "VIN integrity",
            "E25" to "California OHV sticker",
        )
        extra.forEach { (coordinate, label) ->
            val pair = metricCard(label, engine.value(coordinate).display(), "")
            outputs[coordinate] = pair.second
            body.addView(pair.first)
        }

        body.addView(sectionLabel("MODEL / SERIAL REFERENCE"))
        body.addView(staticTable(sheet, 2..39, 10..15))

        fun refresh() {
            outputs.forEach { (coordinate, view) -> view.text = engine.value(coordinate).display() }
            val valid = engine.value("E23").display().contains("mathematically valid", ignoreCase = true)
            statusText.text = if ((input.text?.length ?: 0) == 17) {
                "${if (valid) "✓" else "!"} ${engine.value("E23").display()}\nCheck digit: ${engine.value("F21").display()}"
            } else {
                "Enter all 17 VIN characters"
            }
            statusText.setTextColor(color(if (valid) R.color.banshee_cyan else R.color.banshee_amber))
        }
        input.addTextChangedListener(afterTextChanged { text ->
            val normalized = text.uppercase(Locale.US)
            for (index in 0 until 17) {
                val raw = normalized.getOrNull(index)?.toString().orEmpty()
                val value: Any = raw.toIntOrNull() ?: raw
                engine.set("C${index + 4}", value)
            }
            refresh()
        })
        refresh()
        return scroll
    }

    private fun speedView(sheet: WorkbookSheet): View {
        val engine = FormulaEngine(sheet)
        val (scroll, body) = toolPage("Edit the drivetrain setup to recalculate the complete 1,000–11,000 RPM speed table.")
        val coordinates = listOf("C4", "C5", "C6", "C7", "C9", "C10", "C11", "C12", "C13", "C14")
        val resultViews = linkedMapOf<String, TextView>()
        lateinit var refresh: () -> Unit
        body.addView(sectionLabel("DRIVETRAIN"))
        coordinates.forEach { coordinate ->
            val cell = requireNotNull(sheet.cell(coordinate))
            body.addView(numberInput(rowLabel(sheet, cell), cell.shownValue) {
                engine.set(coordinate, it)
                refresh()
            })
        }
        body.addView(sectionLabel("ESTIMATED MPH BY GEAR"))
        val table = TableLayout(this).apply {
            isStretchAllColumns = false
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        }
        val header = TableRow(this)
        listOf("RPM", "G1", "G2", "G3", "G4", "G5", "G6").forEach { header.addView(tableCell(it, true)) }
        table.addView(header)
        for (row in 6..16) {
            val tableRow = TableRow(this)
            tableRow.addView(tableCell(sheet.cell("E$row")?.shownValue?.toString().orEmpty(), true))
            for (column in 'F'..'K') {
                val coordinate = "$column$row"
                val cell = tableCell(engine.value(coordinate).display(), false)
                resultViews[coordinate] = cell
                tableRow.addView(cell)
            }
            table.addView(tableRow)
        }
        val tableScroll = HorizontalScrollView(this).apply {
            addView(table)
        }
        body.addView(card().apply { addView(tableScroll) })
        body.addView(sectionLabel("SPROCKET RATIO REFERENCE"))
        body.addView(staticTable(sheet, 3..25, 14..22))
        body.addView(safetyNote())

        refresh = {
            resultViews.forEach { (coordinate, view) -> view.text = engine.value(coordinate).display() }
        }
        refresh()
        return scroll
    }

    private fun engineFormulaView(sheet: WorkbookSheet): View {
        val engine = FormulaEngine(sheet)
        val (scroll, body) = toolPage("All original engine-building equations and conversion formulas remain live and editable.")
        val outputs = mutableListOf<Pair<String, TextView>>()
        val groups = mutableListOf<List<WorkbookRow>>()
        var current = mutableListOf<WorkbookRow>()
        var previous = -2
        sheet.rows.filterNot { it.hidden }.forEach { row ->
            if (previous >= 0 && row.index - previous > 1) {
                groups += current
                current = mutableListOf()
            }
            current += row
            previous = row.index
        }
        if (current.isNotEmpty()) groups += current

        groups.forEach { group ->
            val hasInteraction = group.any { row ->
                row.cells.any { it.formula != null || (!it.locked && it.value is Number) }
            }
            if (!hasInteraction) {
                group.forEach { row ->
                    val text = visibleRowText(sheet, row)
                    if (text.isNotBlank()) body.addView(bodyText(text).apply {
                        setPadding(2.dp, 5.dp, 2.dp, 5.dp)
                    })
                }
                return@forEach
            }
            val panel = card().apply {
                layoutParams = linearParams(match, wrap).withMargins(bottom = 12.dp)
            }
            val panelBody = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
            }
            val title = group.firstNotNullOfOrNull { row ->
                row.cells.firstOrNull { it.column == 2 && it.value is String }?.value?.toString()
            } ?: "Formula block"
            panelBody.addView(sectionLabel(title.uppercase(Locale.US)))

            group.forEach { row ->
                val label = row.cells
                    .filter { it.formula == null && it.value is String && it.column <= 2 }
                    .joinToString(" ") { it.value.toString() }
                if (label.isNotBlank() && label != title) panelBody.addView(captionText(label))
                row.cells.filter { !it.locked && it.formula == null && it.value is Number }.forEach { cell ->
                    panelBody.addView(numberInput(
                        label.ifBlank { cell.coordinate },
                        cell.value,
                    ) { value ->
                        engine.set(cell.coordinate, value)
                        outputs.forEach { (coordinate, view) -> view.text = engine.value(coordinate).display() }
                    })
                }
                row.cells.filter { it.formula != null }.forEach { cell ->
                    val unit = row.cells.firstOrNull {
                        it.column > cell.column && it.formula == null && it.value is String
                    }?.value?.toString().orEmpty()
                    val metric = compactMetric(
                        cell.coordinate,
                        engine.value(cell.coordinate).display(),
                        unit,
                    )
                    outputs += cell.coordinate to metric.second
                    panelBody.addView(metric.first)
                }
            }
            panel.addView(panelBody)
            body.addView(panel)
        }
        outputs.forEach { (coordinate, view) -> view.text = engine.value(coordinate).display() }
        addImages(body, sheet)
        return scroll
    }

    private fun angleAreaView(sheet: WorkbookSheet): View {
        val (scroll, body) = toolPage("Native rendering of the workbook's port angle-area chart and source values.")
        val exhaust = (2..8).mapNotNull { row ->
            val x = (sheet.cell("V$row")?.shownValue as? Number)?.toDouble()
            val y = (sheet.cell("W$row")?.shownValue as? Number)?.toDouble()
            if (x == null || y == null) null else x to y
        }
        val transfer = (2..8).mapNotNull { row ->
            val x = (sheet.cell("V$row")?.shownValue as? Number)?.toDouble()
            val y = (sheet.cell("X$row")?.shownValue as? Number)?.toDouble()
            if (x == null || y == null) null else x to y
        }
        body.addView(card().apply {
            addView(AngleAreaChartView(this@MainActivity, exhaust, transfer), frameParams(match, 340.dp))
        })
        body.addView(sectionLabel("SOURCE VALUES"))
        body.addView(staticTable(sheet, 2..8, 22..25))
        return scroll
    }

    private fun referenceView(tool: ToolSpec, sheet: WorkbookSheet): View {
        val (scroll, body) = toolPage(tool.subtitle)
        addSmallInteractiveParity(body, sheet)
        sheet.rows.filterNot { it.hidden }.forEach { row ->
            val value = visibleRowText(sheet, row)
            if (value.isBlank()) return@forEach
            val heading = row.cells.any { it.bold && (it.fontSize ?: 0.0) >= 12.0 }
            if (heading) {
                body.addView(sectionLabel(value.uppercase(Locale.US)))
            } else {
                body.addView(card().apply {
                    layoutParams = linearParams(match, wrap).withMargins(bottom = 7.dp)
                    addView(bodyText(value).apply {
                        setPadding(13.dp, 11.dp, 13.dp, 11.dp)
                        if (row.cells.size > 2) typeface = Typeface.MONOSPACE
                    })
                })
            }
        }
        if (sheet.images.isNotEmpty()) {
            body.addView(sectionLabel("WORKBOOK ILLUSTRATIONS"))
            sheet.images.forEach { body.addView(workbookImage(it.asset, it.width, it.height)) }
            body.addView(captionText("Pinch zoom is provided by Android accessibility magnification; images are retained at their original workbook resolution."))
        }
        return scroll
    }

    private fun addSmallInteractiveParity(body: LinearLayout, sheet: WorkbookSheet) {
        val inputs = sheet.cells.values.filter {
            !it.locked && it.formula == null && it.value is Number
        }
        val formulas = sheet.cells.values.filter { it.formula != null }
        if (inputs.isEmpty() || inputs.size > 10 || formulas.size > 10) return
        val engine = FormulaEngine(sheet)
        val outputs = formulas.associate { cell ->
            val metric = compactMetric(cell.coordinate, engine.value(cell.coordinate).display(), rowUnit(sheet, cell))
            body.addView(metric.first)
            cell.coordinate to metric.second
        }
        inputs.forEach { cell ->
            body.addView(numberInput(rowLabel(sheet, cell), cell.value) {
                engine.set(cell.coordinate, it)
                outputs.forEach { (coordinate, view) -> view.text = engine.value(coordinate).display() }
            })
        }
    }

    private fun addImages(body: LinearLayout, sheet: WorkbookSheet) {
        if (sheet.images.isEmpty()) return
        body.addView(sectionLabel("WORKBOOK DIAGRAMS"))
        sheet.images.forEach { body.addView(workbookImage(it.asset, it.width, it.height)) }
    }

    private fun workbookImage(asset: String, width: Int, height: Int): View {
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Workbook illustration"
            val bitmap = assets.open("workbook_media/$asset").use(android.graphics.BitmapFactory::decodeStream)
            setImageBitmap(bitmap)
            setBackgroundColor(Color.WHITE)
            setPadding(6.dp, 6.dp, 6.dp, 6.dp)
        }
        return card().apply {
            layoutParams = linearParams(match, wrap).withMargins(bottom = 10.dp)
            val targetHeight = max(160.dp, ((resources.displayMetrics.widthPixels - 48.dp) * height / max(width, 1)))
            addView(image, frameParams(match, targetHeight.coerceAtMost(720.dp)))
        }
    }

    private fun staticTable(sheet: WorkbookSheet, rows: IntRange, columns: IntRange): View {
        val table = TableLayout(this).apply {
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
        }
        rows.forEach { row ->
            val values = columns.map { column ->
                sheet.rows.firstOrNull { it.index == row }?.cells?.firstOrNull { it.column == column }?.shownValue
            }
            if (values.all { it == null }) return@forEach
            val line = TableRow(this)
            values.forEachIndexed { index, value ->
                line.addView(tableCell(value?.toString().orEmpty(), row == rows.first || index == 0))
            }
            table.addView(line)
        }
        return card().apply {
            addView(HorizontalScrollView(this@MainActivity).apply { addView(table) })
        }
    }

    private fun visibleRowText(sheet: WorkbookSheet, row: WorkbookRow): String =
        row.cells
            .filter { it.column !in sheet.hiddenColumns }
            .mapNotNull { cell ->
                val raw = cell.shownValue ?: return@mapNotNull null
                val text = raw.toString().trim()
                if (text.isEmpty()) null else text
            }
            .distinct()
            .joinToString("  •  ")

    private fun numberInput(label: String, initial: Any?, changed: (Double) -> Unit): View {
        val layout = TextInputLayout(this).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = linearParams(match, wrap).withMargins(bottom = 9.dp)
        }
        val edit = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setSingleLine(true)
            setText(initial?.toString().orEmpty())
            setSelectAllOnFocus(true)
        }
        layout.addView(edit)
        edit.addTextChangedListener(afterTextChanged {
            it.toDoubleOrNull()?.let(changed)
        })
        return layout
    }

    private fun metricCard(label: String, value: String, unit: String): Pair<View, TextView> {
        val panel = card().apply {
            layoutParams = linearParams(match, wrap).withMargins(bottom = 9.dp)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 13.dp, 16.dp, 13.dp)
        }
        body.addView(captionText(label))
        val output = TextView(this).apply {
            text = value
            setTextColor(color(R.color.banshee_cyan))
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
        }
        body.addView(output)
        if (unit.isNotBlank()) body.addView(captionText(unit))
        panel.addView(body)
        return panel to output
    }

    private fun compactMetric(label: String, value: String, unit: String): Pair<View, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6.dp, 0, 6.dp)
        }
        row.addView(captionText(label), linearParams(0, wrap, 1f))
        val output = TextView(this).apply {
            text = value
            setTextColor(color(R.color.banshee_cyan))
            textSize = 16f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
        }
        row.addView(output, linearParams(0, wrap, 1f))
        if (unit.isNotBlank()) row.addView(captionText("  $unit"))
        return row to output
    }

    private fun resultMetric(parent: LinearLayout, label: String): TextView {
        parent.addView(captionText(label))
        return TextView(this).apply {
            setTextColor(color(R.color.banshee_cyan))
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
            parent.addView(this)
        }
    }

    private fun rowLabel(sheet: WorkbookSheet, cell: WorkbookCell): String =
        sheet.rows.firstOrNull { it.index == cell.row }?.cells
            ?.filter { it.column < cell.column && it.formula == null && it.value is String }
            ?.maxByOrNull { it.column }
            ?.value?.toString()?.trim().orEmpty()
            .ifBlank { cell.coordinate }

    private fun rowUnit(sheet: WorkbookSheet, cell: WorkbookCell): String =
        sheet.rows.firstOrNull { it.index == cell.row }?.cells
            ?.firstOrNull { it.column > cell.column && it.formula == null && it.value is String }
            ?.value?.toString()?.trim().orEmpty()

    private fun tableCell(value: String, header: Boolean): TextView = TextView(this).apply {
        text = value
        setTextColor(color(if (header) R.color.banshee_amber else R.color.banshee_white))
        textSize = 13f
        typeface = if (header) Typeface.DEFAULT_BOLD else Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(12.dp, 10.dp, 12.dp, 10.dp)
        minWidth = 72.dp
        setBackgroundColor(color(if (header) R.color.banshee_surface_high else R.color.banshee_surface))
    }

    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        radius = 14.dp.toFloat()
        cardElevation = 0f
        setCardBackgroundColor(color(R.color.banshee_surface))
        strokeWidth = 1.dp
        strokeColor = color(R.color.banshee_surface_high)
    }

    private fun sectionLabel(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(color(R.color.banshee_amber))
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = .1f
        setPadding(2.dp, 18.dp, 2.dp, 8.dp)
    }

    private fun bodyText(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(color(R.color.banshee_muted))
        textSize = 15f
        setLineSpacing(0f, 1.16f)
    }

    private fun captionText(value: String): TextView = TextView(this).apply {
        text = value
        setTextColor(color(R.color.banshee_muted))
        textSize = 12f
        setLineSpacing(0f, 1.12f)
    }

    private fun safetyNote(): View = card().apply {
        layoutParams = linearParams(match, wrap).withMargins(top = 14.dp)
        addView(bodyText("Baseline guidance only. Verify plug color, detonation margin, fuel quality, temperature, and manufacturer specifications before sustained high-load operation.").apply {
            setPadding(14.dp, 14.dp, 14.dp, 14.dp)
        })
    }

    private fun color(resource: Int): Int = ContextCompat.getColor(this, resource)
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun afterTextChanged(action: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(text: Editable?) = action(text?.toString().orEmpty())
    }

    private data class SimpleConfig(val inputs: List<String>, val outputs: List<String>)

    companion object {
        private const val match = ViewGroup.LayoutParams.MATCH_PARENT
        private const val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        private fun linearParams(width: Int, height: Int, weight: Float = 0f) =
            LinearLayout.LayoutParams(width, height, weight)

        private fun frameParams(width: Int, height: Int) = FrameLayout.LayoutParams(width, height)

        private fun LinearLayout.LayoutParams.withMargins(
            left: Int = 0,
            top: Int = 0,
            right: Int = 0,
            bottom: Int = 0,
        ) = apply { setMargins(left, top, right, bottom) }
    }
}

private class ToolAdapter(
    private val selected: (ToolSpec) -> Unit,
) : RecyclerView.Adapter<ToolAdapter.Holder>() {
    private val items = mutableListOf<ToolSpec>()

    fun submit(newItems: List<ToolSpec>) {
        val oldItems = items.toList()
        val changes = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size
                override fun getNewListSize() = newItems.size
                override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                    oldItems[oldPosition].sheet == newItems[newPosition].sheet

                override fun areContentsTheSame(oldPosition: Int, newPosition: Int) =
                    oldItems[oldPosition] == newItems[newPosition]
            },
        )
        items.clear()
        items.addAll(newItems)
        changes.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val card = MaterialCardView(context).apply {
            radius = dp(14).toFloat()
            strokeWidth = dp(1)
            strokeColor = ContextCompat.getColor(context, R.color.banshee_surface_high)
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.banshee_surface))
            isClickable = true
            isFocusable = true
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(178)).apply {
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
        }
        val glyph = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.banshee_cyan))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = .08f
        }
        val title = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.banshee_white))
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            setPadding(0, dp(8), 0, dp(5))
        }
        val subtitle = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.banshee_muted))
            textSize = 12f
            maxLines = 4
        }
        body.addView(glyph)
        body.addView(title)
        body.addView(subtitle)
        card.addView(body)
        return Holder(card, glyph, title, subtitle, selected)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    class Holder(
        private val card: MaterialCardView,
        private val glyph: TextView,
        private val title: TextView,
        private val subtitle: TextView,
        private val selected: (ToolSpec) -> Unit,
    ) : RecyclerView.ViewHolder(card) {
        fun bind(tool: ToolSpec) {
            glyph.text = card.context.getString(
                R.string.tool_glyph_format,
                tool.glyph,
                tool.category.uppercase(Locale.US),
            )
            title.text = tool.title
            subtitle.text = tool.subtitle
            card.contentDescription = "${tool.title}. ${tool.subtitle}"
            card.setOnClickListener { selected(tool) }
        }
    }
}

@SuppressLint("ViewConstructor")
private class AngleAreaChartView(
    context: Context,
    private val exhaust: List<Pair<Double, Double>>,
    private val transfer: List<Pair<Double, Double>>,
) : View(context) {
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.banshee_muted)
        strokeWidth = 1.5f * resources.displayMetrics.density
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    private val exhaustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.banshee_cyan)
        strokeWidth = 3f * resources.displayMetrics.density
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        style = Paint.Style.STROKE
    }
    private val transferPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.banshee_amber)
        strokeWidth = 3f * resources.displayMetrics.density
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        style = Paint.Style.STROKE
    }
    private val seriesPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = 54f * resources.displayMetrics.density
        val right = width - 20f * resources.displayMetrics.density
        val top = 38f * resources.displayMetrics.density
        val bottom = height - 45f * resources.displayMetrics.density
        canvas.drawLine(left, top, left, bottom, axis)
        canvas.drawLine(left, bottom, right, bottom, axis)
        canvas.drawText("PORT AREA", left, 23f * resources.displayMetrics.density, axis)
        canvas.drawText("TIME-AREA", left, height - 14f * resources.displayMetrics.density, axis)
        canvas.drawText("EXHAUST", right - 145f * resources.displayMetrics.density, top, exhaustPaint.apply { style = Paint.Style.FILL })
        canvas.drawText("TRANSFER", right - 70f * resources.displayMetrics.density, top, transferPaint.apply { style = Paint.Style.FILL })
        exhaustPaint.style = Paint.Style.STROKE
        transferPaint.style = Paint.Style.STROKE

        val all = exhaust + transfer
        if (all.isEmpty()) return
        val minX = all.minOf { it.first }
        val maxX = all.maxOf { it.first }
        val maxY = all.maxOf { it.second }
        fun drawSeries(data: List<Pair<Double, Double>>, paint: Paint) {
            seriesPath.reset()
            data.sortedBy { it.first }.forEachIndexed { index, point ->
                val x = left + ((point.first - minX) / (maxX - minX).coerceAtLeast(0.000001) * (right - left)).toFloat()
                val y = bottom - (point.second / maxY.coerceAtLeast(1.0) * (bottom - top)).toFloat()
                if (index == 0) seriesPath.moveTo(x, y) else seriesPath.lineTo(x, y)
                canvas.drawCircle(x, y, 4f * resources.displayMetrics.density, paint.apply { style = Paint.Style.FILL })
                paint.style = Paint.Style.STROKE
            }
            canvas.drawPath(seriesPath, paint)
        }
        drawSeries(exhaust, exhaustPaint)
        drawSeries(transfer, transferPaint)
    }
}

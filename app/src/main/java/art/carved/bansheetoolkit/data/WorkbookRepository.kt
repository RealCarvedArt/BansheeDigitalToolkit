package art.carved.bansheetoolkit.data

import java.io.InputStream

data class WorkbookSource(
    val name: String,
    val sha256: String,
    val sheetCount: Int,
    val formulaCount: Int,
    val mediaCount: Int,
)

data class WorkbookCell(
    val coordinate: String,
    val row: Int,
    val column: Int,
    val value: Any?,
    val formula: String?,
    val cached: Any?,
    val locked: Boolean,
    val fill: String?,
    val bold: Boolean,
    val fontSize: Double?,
    val hyperlink: String?,
) {
    val shownValue: Any?
        get() = if (formula == null) value else cached
}

data class WorkbookRow(
    val index: Int,
    val hidden: Boolean,
    val cells: List<WorkbookCell>,
)

data class WorkbookImage(
    val asset: String,
    val width: Int,
    val height: Int,
    val anchorRow: Int,
    val anchorColumn: Int,
)

data class WorkbookSheet(
    val name: String,
    val formulaCount: Int,
    val rows: List<WorkbookRow>,
    val images: List<WorkbookImage>,
    val hiddenColumns: Set<Int>,
) {
    val cells: Map<String, WorkbookCell> = rows
        .flatMap { it.cells }
        .associateBy { it.coordinate.uppercase() }

    fun cell(coordinate: String): WorkbookCell? = cells[coordinate.uppercase()]
}

class WorkbookRepository private constructor(
    val source: WorkbookSource,
    private val sheetFiles: Map<String, String>,
    private val sheetLoader: (String) -> String,
    eagerSheets: List<WorkbookSheet> = emptyList(),
) {
    private val sheetsByName = eagerSheets.associateBy { it.name }.toMutableMap()
    private val sheetNames = if (sheetFiles.isNotEmpty()) sheetFiles.keys.toList() else eagerSheets.map { it.name }

    val sheets: List<WorkbookSheet>
        get() = sheetNames.map(::sheet)

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    fun sheet(name: String): WorkbookSheet {
        sheetsByName[name]?.let { return it }
        val filename = requireNotNull(sheetFiles[name]) { "Workbook sheet not found: $name" }
        val parsed = parseSheet(MiniJson.parse(sheetLoader(filename)) as Map<String, Any?>)
        require(parsed.name == name) { "Sheet file $filename contained ${parsed.name}, expected $name" }
        sheetsByName[name] = parsed
        return parsed
    }

    companion object {
        fun load(input: InputStream, sheetLoader: (String) -> InputStream): WorkbookRepository =
            fromJson(
                input.bufferedReader(Charsets.UTF_8).use { it.readText() },
            ) { filename ->
                sheetLoader(filename).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

        @Suppress("UNCHECKED_CAST")
        fun fromJson(json: String, sheetLoader: (String) -> String = { error("No sheet loader") }): WorkbookRepository {
            val root = MiniJson.parse(json) as Map<String, Any?>
            val sourceMap = root.map("source")
            val summary = root.map("summary")
            val source = WorkbookSource(
                name = sourceMap.string("name"),
                sha256 = sourceMap.string("sha256"),
                sheetCount = summary.int("sheet_count"),
                formulaCount = summary.int("formula_count"),
                mediaCount = summary.int("media_count"),
            )
            val eagerSheets = root.list("sheets").map { parseSheet(it as Map<String, Any?>) }
            val files = root.list("sheet_files")
                .map { it as Map<String, Any?> }
                .associate { it.string("name") to it.string("file") }
            val count = if (files.isNotEmpty()) files.size else eagerSheets.size
            require(source.sheetCount == count) {
                "Workbook manifest expected ${source.sheetCount} sheets but contained $count"
            }
            return WorkbookRepository(source, files, sheetLoader, eagerSheets)
        }

        @Suppress("UNCHECKED_CAST")
        private fun parseSheet(sheet: Map<String, Any?>): WorkbookSheet {
            val hiddenColumns = sheet.list("columns")
                .map { it as Map<String, Any?> }
                .filter { it.boolean("hidden") }
                .map { lettersToColumn(it.string("column")) }
                .toSet()
            return WorkbookSheet(
                name = sheet.string("name"),
                formulaCount = sheet.int("formula_count"),
                rows = sheet.list("rows").map { rawRow ->
                    val row = rawRow as Map<String, Any?>
                    WorkbookRow(
                        index = row.int("index"),
                        hidden = row.boolean("hidden"),
                        cells = row.list("cells").map { rawCell ->
                            val cell = rawCell as Map<String, Any?>
                            val font = cell.map("font")
                            WorkbookCell(
                                coordinate = cell.string("coordinate"),
                                row = cell.int("row"),
                                column = cell.int("column"),
                                value = cell["value"],
                                formula = cell["formula"] as? String,
                                cached = cell["cached"],
                                locked = cell.boolean("locked"),
                                fill = cell["fill"] as? String,
                                bold = font.boolean("bold"),
                                fontSize = (font["size"] as? Number)?.toDouble(),
                                hyperlink = cell["hyperlink"] as? String,
                            )
                        },
                    )
                },
                images = sheet.list("images").mapNotNull { rawImage ->
                    val image = rawImage as Map<String, Any?>
                    val asset = image["asset"] as? String ?: return@mapNotNull null
                    val anchor = image.map("anchor")
                    WorkbookImage(
                        asset = asset,
                        width = image.int("width"),
                        height = image.int("height"),
                        anchorRow = anchor.int("row"),
                        anchorColumn = anchor.int("column"),
                    )
                },
                hiddenColumns = hiddenColumns,
            )
        }

        private fun lettersToColumn(letters: String): Int {
            var result = 0
            for (letter in letters.uppercase()) result = result * 26 + (letter - 'A' + 1)
            return result
        }

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.map(key: String): Map<String, Any?> =
            this[key] as? Map<String, Any?> ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.list(key: String): List<Any?> =
            this[key] as? List<Any?> ?: emptyList()

        private fun Map<String, Any?>.string(key: String): String = this[key]?.toString().orEmpty()
        private fun Map<String, Any?>.int(key: String): Int = (this[key] as Number).toInt()
        private fun Map<String, Any?>.boolean(key: String): Boolean = this[key] as? Boolean ?: false
    }
}

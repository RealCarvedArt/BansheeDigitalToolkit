package art.carved.bansheetoolkit.formula

import art.carved.bansheetoolkit.data.WorkbookSheet
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface ExcelValue {
    data class NumberValue(val number: Double) : ExcelValue
    data class TextValue(val text: String) : ExcelValue
    data class BooleanValue(val boolean: Boolean) : ExcelValue
    data class RangeValue(val values: List<ExcelValue>) : ExcelValue
    data class ErrorValue(val message: String) : ExcelValue
    data object BlankValue : ExcelValue

    fun display(): String = when (this) {
        is NumberValue -> generalNumber(number)
        is TextValue -> text
        is BooleanValue -> if (boolean) "TRUE" else "FALSE"
        is RangeValue -> values.joinToString(", ") { it.display() }
        is ErrorValue -> "#ERROR"
        BlankValue -> ""
    }

    companion object {
        fun fromRaw(value: Any?): ExcelValue = when (value) {
            null -> BlankValue
            is Number -> NumberValue(value.toDouble())
            is Boolean -> BooleanValue(value)
            else -> TextValue(value.toString())
        }

        fun generalNumber(value: Double): String {
            if (!value.isFinite()) return "#NUM!"
            if (value == 0.0) return "0"
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        }
    }
}

class FormulaEngine(private val sheet: WorkbookSheet) {
    private val overrides = mutableMapOf<String, ExcelValue>()
    private val memo = mutableMapOf<String, ExcelValue>()
    private val evaluating = mutableSetOf<String>()

    fun set(coordinate: String, value: Any?) {
        overrides[coordinate.uppercase(Locale.US)] = ExcelValue.fromRaw(value)
        memo.clear()
    }

    fun clear(coordinate: String) {
        overrides.remove(coordinate.uppercase(Locale.US))
        memo.clear()
    }

    fun value(coordinate: String): ExcelValue {
        val normalized = coordinate.replace("$", "").uppercase(Locale.US)
        overrides[normalized]?.let { return it }
        memo[normalized]?.let { return it }
        val cell = sheet.cell(normalized) ?: return ExcelValue.BlankValue
        if (cell.formula == null) return ExcelValue.fromRaw(cell.value)
        if (!evaluating.add(normalized)) return ExcelValue.ErrorValue("Circular reference: $normalized")
        val result = try {
            FormulaParser(cell.formula).parse().evaluate(this)
        } catch (error: RuntimeException) {
            ExcelValue.ErrorValue(error.message ?: "Formula failed")
        } finally {
            evaluating.remove(normalized)
        }
        memo[normalized] = result
        return result
    }

    internal fun range(start: String, end: String): ExcelValue.RangeValue {
        val first = CellAddress.parse(start)
        val last = CellAddress.parse(end)
        val values = mutableListOf<ExcelValue>()
        for (row in minOf(first.row, last.row)..maxOf(first.row, last.row)) {
            for (column in minOf(first.column, last.column)..maxOf(first.column, last.column)) {
                values += value(CellAddress(column, row).coordinate)
            }
        }
        return ExcelValue.RangeValue(values)
    }
}

private data class CellAddress(val column: Int, val row: Int) {
    val coordinate: String
        get() {
            var remaining = column
            val result = StringBuilder()
            while (remaining > 0) {
                remaining--
                result.append(('A'.code + remaining % 26).toChar())
                remaining /= 26
            }
            return result.reverse().toString() + row
        }

    companion object {
        private val pattern = Regex("""\$?([A-Za-z]{1,3})\$?(\d+)""")

        fun parse(value: String): CellAddress {
            val match = requireNotNull(pattern.matchEntire(value)) { "Invalid cell reference: $value" }
            var column = 0
            for (letter in match.groupValues[1].uppercase(Locale.US)) {
                column = column * 26 + (letter - 'A' + 1)
            }
            return CellAddress(column, match.groupValues[2].toInt())
        }

        fun isReference(value: String): Boolean = pattern.matches(value)
    }
}

private sealed interface FormulaNode {
    fun evaluate(engine: FormulaEngine): ExcelValue
}

private data class LiteralNode(val value: ExcelValue) : FormulaNode {
    override fun evaluate(engine: FormulaEngine): ExcelValue = value
}

private data class CellNode(val reference: String) : FormulaNode {
    override fun evaluate(engine: FormulaEngine): ExcelValue = engine.value(reference)
}

private data class RangeNode(val start: String, val end: String) : FormulaNode {
    override fun evaluate(engine: FormulaEngine): ExcelValue = engine.range(start, end)
}

private data class UnaryNode(val operator: String, val operand: FormulaNode) : FormulaNode {
    override fun evaluate(engine: FormulaEngine): ExcelValue {
        val value = operand.evaluate(engine)
        if (value is ExcelValue.ErrorValue) return value
        return when (operator) {
            "+" -> ExcelValue.NumberValue(value.asNumber())
            "-" -> ExcelValue.NumberValue(-value.asNumber())
            else -> ExcelValue.ErrorValue("Unknown unary operator $operator")
        }
    }
}

private data class BinaryNode(
    val left: FormulaNode,
    val operator: String,
    val right: FormulaNode,
) : FormulaNode {
    override fun evaluate(engine: FormulaEngine): ExcelValue {
        val first = left.evaluate(engine)
        if (first is ExcelValue.ErrorValue) return first
        val second = right.evaluate(engine)
        if (second is ExcelValue.ErrorValue) return second
        return when (operator) {
            "+" -> ExcelValue.NumberValue(first.asNumber() + second.asNumber())
            "-" -> ExcelValue.NumberValue(first.asNumber() - second.asNumber())
            "*" -> ExcelValue.NumberValue(first.asNumber() * second.asNumber())
            "/" -> if (second.asNumber() == 0.0) {
                ExcelValue.ErrorValue("Division by zero")
            } else {
                ExcelValue.NumberValue(first.asNumber() / second.asNumber())
            }
            "^" -> ExcelValue.NumberValue(first.asNumber().pow(second.asNumber()))
            "&" -> ExcelValue.TextValue(first.asText() + second.asText())
            "=" -> ExcelValue.BooleanValue(first.compare(second) == 0)
            "<>" -> ExcelValue.BooleanValue(first.compare(second) != 0)
            "<" -> ExcelValue.BooleanValue(first.compare(second) < 0)
            ">" -> ExcelValue.BooleanValue(first.compare(second) > 0)
            "<=" -> ExcelValue.BooleanValue(first.compare(second) <= 0)
            ">=" -> ExcelValue.BooleanValue(first.compare(second) >= 0)
            else -> ExcelValue.ErrorValue("Unknown operator $operator")
        }
    }
}

private data class FunctionNode(val name: String, val arguments: List<FormulaNode>) : FormulaNode {
    override fun evaluate(engine: FormulaEngine): ExcelValue {
        if (name == "IF") {
            require(arguments.size >= 2) { "IF requires at least two arguments" }
            return if (arguments[0].evaluate(engine).asBoolean()) {
                arguments[1].evaluate(engine)
            } else {
                arguments.getOrNull(2)?.evaluate(engine) ?: ExcelValue.BooleanValue(false)
            }
        }

        val values = arguments.map { it.evaluate(engine) }
        values.firstOrNull { it is ExcelValue.ErrorValue }?.let { return it }
        val flattened = values.flatMap { it.flatten() }
        return when (name) {
            "SUM" -> ExcelValue.NumberValue(flattened.sumOfNumbers())
            "AVERAGE" -> {
                val numbers = flattened.numbers()
                if (numbers.isEmpty()) ExcelValue.ErrorValue("AVERAGE has no numbers")
                else ExcelValue.NumberValue(numbers.average())
            }
            "MAX" -> ExcelValue.NumberValue(flattened.numbers().maxOrNull() ?: 0.0)
            "AND" -> ExcelValue.BooleanValue(flattened.all { it.asBoolean() })
            "OR" -> ExcelValue.BooleanValue(flattened.any { it.asBoolean() })
            "ISNUMBER" -> ExcelValue.BooleanValue(values.firstOrNull() is ExcelValue.NumberValue)
            "VALUE" -> {
                val number = values.firstOrNull()?.asText()?.trim()?.toDoubleOrNull()
                if (number == null) ExcelValue.ErrorValue("VALUE is not numeric")
                else ExcelValue.NumberValue(number)
            }
            "ABS" -> unaryNumber(values) { abs(it) }
            "SQRT" -> unaryNumber(values) { sqrt(it) }
            "COS" -> unaryNumber(values) { cos(it) }
            "SIN" -> unaryNumber(values) { sin(it) }
            "ACOS" -> unaryNumber(values) { acos(it) }
            "PI" -> ExcelValue.NumberValue(Math.PI)
            "MOD" -> {
                val number = values.requiredNumber(0)
                val divisor = values.requiredNumber(1)
                if (divisor == 0.0) ExcelValue.ErrorValue("MOD division by zero")
                else ExcelValue.NumberValue(number - divisor * floor(number / divisor))
            }
            "ROUND" -> rounded(values, RoundingMode.HALF_UP)
            "ROUNDDOWN" -> rounded(values, RoundingMode.DOWN)
            "ROUNDUP" -> rounded(values, RoundingMode.UP)
            "CEILING" -> {
                val number = values.requiredNumber(0)
                val significance = abs(values.requiredNumber(1))
                if (significance == 0.0) ExcelValue.NumberValue(0.0)
                else ExcelValue.NumberValue(ceil(number / significance) * significance)
            }
            "EVEN" -> {
                val number = values.requiredNumber(0)
                val result = ceil(abs(number) / 2.0) * 2.0
                ExcelValue.NumberValue(if (number < 0) -result else result)
            }
            else -> ExcelValue.ErrorValue("Unsupported Excel function $name")
        }
    }

    private fun unaryNumber(values: List<ExcelValue>, operation: (Double) -> Double): ExcelValue =
        ExcelValue.NumberValue(operation(values.requiredNumber(0)))

    private fun rounded(values: List<ExcelValue>, mode: RoundingMode): ExcelValue {
        val number = values.requiredNumber(0)
        val digits = values.getOrNull(1)?.asNumber()?.toInt() ?: 0
        val decimal = BigDecimal.valueOf(number)
        val rounded = if (digits >= 0) {
            decimal.setScale(digits, mode)
        } else {
            decimal.movePointLeft(-digits).setScale(0, mode).movePointRight(-digits)
        }
        return ExcelValue.NumberValue(rounded.toDouble())
    }
}

private fun ExcelValue.flatten(): List<ExcelValue> =
    if (this is ExcelValue.RangeValue) values.flatMap { it.flatten() } else listOf(this)

private fun List<ExcelValue>.numbers(): List<Double> =
    mapNotNull { (it as? ExcelValue.NumberValue)?.number }

private fun List<ExcelValue>.sumOfNumbers(): Double = numbers().sum()

private fun List<ExcelValue>.requiredNumber(index: Int): Double =
    getOrNull(index)?.asNumber() ?: error("Missing numeric argument ${index + 1}")

private fun ExcelValue.asNumber(): Double = when (this) {
    is ExcelValue.NumberValue -> number
    is ExcelValue.BooleanValue -> if (boolean) 1.0 else 0.0
    is ExcelValue.TextValue -> text.trim().toDoubleOrNull() ?: 0.0
    is ExcelValue.RangeValue -> values.firstOrNull()?.asNumber() ?: 0.0
    is ExcelValue.ErrorValue -> Double.NaN
    ExcelValue.BlankValue -> 0.0
}

private fun ExcelValue.asText(): String = when (this) {
    is ExcelValue.TextValue -> text
    is ExcelValue.NumberValue -> ExcelValue.generalNumber(number)
    is ExcelValue.BooleanValue -> if (boolean) "TRUE" else "FALSE"
    is ExcelValue.RangeValue -> values.joinToString(",") { it.asText() }
    is ExcelValue.ErrorValue -> "#ERROR"
    ExcelValue.BlankValue -> ""
}

private fun ExcelValue.asBoolean(): Boolean = when (this) {
    is ExcelValue.BooleanValue -> boolean
    is ExcelValue.NumberValue -> number != 0.0
    is ExcelValue.TextValue -> text.isNotBlank() && !text.equals("FALSE", ignoreCase = true)
    is ExcelValue.RangeValue -> values.any { it.asBoolean() }
    is ExcelValue.ErrorValue, ExcelValue.BlankValue -> false
}

private fun ExcelValue.compare(other: ExcelValue): Int {
    if (this is ExcelValue.NumberValue && other is ExcelValue.NumberValue) {
        return number.compareTo(other.number)
    }
    return asText().compareTo(other.asText(), ignoreCase = true)
}

private class FormulaParser(formula: String) {
    private val tokenizer = FormulaTokenizer(formula.removePrefix("="))
    private var current = tokenizer.next()

    fun parse(): FormulaNode {
        val result = comparison()
        require(current.type == TokenType.END) { "Unexpected token '${current.text}'" }
        return result
    }

    private fun comparison(): FormulaNode {
        var result = concatenation()
        while (current.text in setOf("=", "<>", "<", ">", "<=", ">=")) {
            val operator = take().text
            result = BinaryNode(result, operator, concatenation())
        }
        return result
    }

    private fun concatenation(): FormulaNode {
        var result = addition()
        while (current.text == "&") {
            result = BinaryNode(result, take().text, addition())
        }
        return result
    }

    private fun addition(): FormulaNode {
        var result = multiplication()
        while (current.text == "+" || current.text == "-") {
            result = BinaryNode(result, take().text, multiplication())
        }
        return result
    }

    private fun multiplication(): FormulaNode {
        var result = power()
        while (current.text == "*" || current.text == "/") {
            result = BinaryNode(result, take().text, power())
        }
        return result
    }

    private fun power(): FormulaNode {
        var result = unary()
        if (current.text == "^") result = BinaryNode(result, take().text, power())
        return result
    }

    private fun unary(): FormulaNode {
        if (current.text == "+" || current.text == "-") {
            return UnaryNode(take().text, unary())
        }
        return primary()
    }

    private fun primary(): FormulaNode {
        if (current.type == TokenType.NUMBER) {
            return LiteralNode(ExcelValue.NumberValue(take().text.toDouble()))
        }
        if (current.type == TokenType.STRING) {
            return LiteralNode(ExcelValue.TextValue(take().text))
        }
        if (current.type == TokenType.IDENTIFIER) {
            val identifier = take().text
            if (current.type == TokenType.LEFT_PAREN) {
                take()
                val arguments = mutableListOf<FormulaNode>()
                if (current.type != TokenType.RIGHT_PAREN) {
                    do {
                        arguments += comparison()
                        if (current.type != TokenType.COMMA) break
                        take()
                    } while (true)
                }
                require(current.type == TokenType.RIGHT_PAREN) { "Expected ')' after $identifier" }
                take()
                return FunctionNode(identifier.uppercase(Locale.US), arguments)
            }
            require(CellAddress.isReference(identifier)) { "Unknown identifier $identifier" }
            if (current.type == TokenType.COLON) {
                take()
                require(current.type == TokenType.IDENTIFIER) { "Expected cell after ':'" }
                return RangeNode(identifier, take().text)
            }
            return CellNode(identifier)
        }
        if (current.type == TokenType.LEFT_PAREN) {
            take()
            val result = comparison()
            require(current.type == TokenType.RIGHT_PAREN) { "Expected ')'" }
            take()
            return result
        }
        error("Unexpected token '${current.text}'")
    }

    private fun take(): FormulaToken = current.also { current = tokenizer.next() }
}

private enum class TokenType {
    NUMBER,
    STRING,
    IDENTIFIER,
    OPERATOR,
    LEFT_PAREN,
    RIGHT_PAREN,
    COMMA,
    COLON,
    END,
}

private data class FormulaToken(val type: TokenType, val text: String)

private class FormulaTokenizer(private val source: String) {
    private var index = 0

    fun next(): FormulaToken {
        while (index < source.length && source[index].isWhitespace()) index++
        if (index >= source.length) return FormulaToken(TokenType.END, "")
        val character = source[index]
        if (character == '"') return stringToken()
        if (character.isDigit() || character == '.') return numberToken()
        if (character.isLetter() || character == '$' || character == '_') return identifierToken()
        index++
        return when (character) {
            '(' -> FormulaToken(TokenType.LEFT_PAREN, "(")
            ')' -> FormulaToken(TokenType.RIGHT_PAREN, ")")
            ',' -> FormulaToken(TokenType.COMMA, ",")
            ':' -> FormulaToken(TokenType.COLON, ":")
            '<', '>' -> {
                val text = if (index < source.length && source[index] in charArrayOf('=', '>')) {
                    "$character${source[index++]}"
                } else {
                    character.toString()
                }
                FormulaToken(TokenType.OPERATOR, text)
            }
            '+', '-', '*', '/', '^', '&', '=' -> FormulaToken(TokenType.OPERATOR, character.toString())
            else -> error("Unexpected formula character '$character' at $index")
        }
    }

    private fun stringToken(): FormulaToken {
        index++
        val result = StringBuilder()
        while (index < source.length) {
            if (source[index] == '"') {
                index++
                if (index < source.length && source[index] == '"') {
                    result.append('"')
                    index++
                    continue
                }
                return FormulaToken(TokenType.STRING, result.toString())
            }
            result.append(source[index++])
        }
        error("Unterminated formula string")
    }

    private fun numberToken(): FormulaToken {
        val start = index
        while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
        if (index < source.length && source[index] in charArrayOf('e', 'E')) {
            index++
            if (index < source.length && source[index] in charArrayOf('+', '-')) index++
            while (index < source.length && source[index].isDigit()) index++
        }
        return FormulaToken(TokenType.NUMBER, source.substring(start, index))
    }

    private fun identifierToken(): FormulaToken {
        val start = index
        while (
            index < source.length &&
            (source[index].isLetterOrDigit() || source[index] in charArrayOf('$', '_', '.'))
        ) {
            index++
        }
        return FormulaToken(TokenType.IDENTIFIER, source.substring(start, index))
    }
}

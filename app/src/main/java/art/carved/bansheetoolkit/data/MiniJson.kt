package art.carved.bansheetoolkit.data

/**
 * Small dependency-free JSON reader. It keeps the workbook contract usable in local JVM tests
 * and avoids adding a network-delivered serialization runtime to the APK.
 */
object MiniJson {
    fun parse(source: String): Any? = Parser(source).parse()

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): Any? {
            val result = value()
            whitespace()
            require(index == source.length) { "Unexpected JSON at offset $index" }
            return result
        }

        private fun value(): Any? {
            whitespace()
            require(index < source.length) { "Unexpected end of JSON" }
            return when (source[index]) {
                '{' -> objectValue()
                '[' -> arrayValue()
                '"' -> stringValue()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> numberValue()
                else -> error("Unexpected '${source[index]}' at offset $index")
            }
        }

        private fun objectValue(): Map<String, Any?> {
            index++
            whitespace()
            val result = linkedMapOf<String, Any?>()
            if (take('}')) return result
            while (true) {
                whitespace()
                val key = stringValue()
                whitespace()
                require(take(':')) { "Expected ':' at offset $index" }
                result[key] = value()
                whitespace()
                if (take('}')) return result
                require(take(',')) { "Expected ',' at offset $index" }
            }
        }

        private fun arrayValue(): List<Any?> {
            index++
            whitespace()
            val result = mutableListOf<Any?>()
            if (take(']')) return result
            while (true) {
                result += value()
                whitespace()
                if (take(']')) return result
                require(take(',')) { "Expected ',' at offset $index" }
            }
        }

        private fun stringValue(): String {
            require(take('"')) { "Expected string at offset $index" }
            val result = StringBuilder()
            while (index < source.length) {
                val character = source[index++]
                when (character) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(index < source.length) { "Incomplete escape at offset $index" }
                        when (val escaped = source[index++]) {
                            '"' -> result.append('"')
                            '\\' -> result.append('\\')
                            '/' -> result.append('/')
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(index + 4 <= source.length) {
                                    "Incomplete unicode escape at offset $index"
                                }
                                result.append(source.substring(index, index + 4).toInt(16).toChar())
                                index += 4
                            }
                            else -> error("Invalid escape '$escaped' at offset $index")
                        }
                    }
                    else -> result.append(character)
                }
            }
            error("Unterminated string")
        }

        private fun numberValue(): Number {
            val start = index
            if (source[index] == '-') index++
            while (index < source.length && source[index].isDigit()) index++
            var decimal = false
            if (index < source.length && source[index] == '.') {
                decimal = true
                index++
                while (index < source.length && source[index].isDigit()) index++
            }
            if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
                decimal = true
                index++
                if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
                while (index < source.length && source[index].isDigit()) index++
            }
            val token = source.substring(start, index)
            return if (decimal) token.toDouble() else token.toLong()
        }

        private fun <T> literal(token: String, result: T): T {
            require(source.startsWith(token, index)) { "Expected $token at offset $index" }
            index += token.length
            return result
        }

        private fun whitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun take(expected: Char): Boolean {
            if (index >= source.length || source[index] != expected) return false
            index++
            return true
        }
    }
}

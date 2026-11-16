package dev.willc.brandbar

internal sealed interface JsonValue {
    data object JsonNull : JsonValue
    data class JsonBool(val value: Boolean) : JsonValue
    data class JsonNum(val value: Double) : JsonValue
    data class JsonStr(val value: String) : JsonValue
    data class JsonArr(val items: List<JsonValue>) : JsonValue
    data class JsonObj(val entries: Map<String, JsonValue>) : JsonValue
}

internal object BrandJson {
    fun parseObject(input: String): JsonValue.JsonObj {
        val parser = Parser(input)
        val root = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.isAtEnd()) {
            throw BrandThemeException("Unexpected trailing JSON content at index ${parser.position}")
        }
        return root as? JsonValue.JsonObj
            ?: throw BrandThemeException("Expected theme.json root to be a JSON object")
    }

    private class Parser(private val source: String) {
        var position: Int = 0
            private set

        fun isAtEnd(): Boolean = position >= source.length

        fun skipWhitespace() {
            while (position < source.length && source[position].isWhitespace()) {
                position++
            }
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (isAtEnd()) {
                throw BrandThemeException("Unexpected end of JSON at index $position")
            }
            return when (val ch = source[position]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.JsonStr(parseString())
                't' -> {
                    expectKeyword("true")
                    JsonValue.JsonBool(true)
                }
                'f' -> {
                    expectKeyword("false")
                    JsonValue.JsonBool(false)
                }
                'n' -> {
                    expectKeyword("null")
                    JsonValue.JsonNull
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw BrandThemeException("Unexpected character '$ch' in JSON at index $position")
            }
        }

        private fun parseObject(): JsonValue.JsonObj {
            expectChar('{')
            skipWhitespace()
            val map = LinkedHashMap<String, JsonValue>()
            if (peekChar() == '}') {
                position++
                return JsonValue.JsonObj(map)
            }
            while (true) {
                skipWhitespace()
                if (peekChar() != '"') {
                    throw BrandThemeException("Expected string property name in JSON object at index $position")
                }
                val key = parseString()
                skipWhitespace()
                expectChar(':')
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (val sep = peekChar()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return JsonValue.JsonObj(map)
                    }
                    else -> throw BrandThemeException("Expected ',' or '}' in JSON object at index $position, got '${sep ?: "EOF"}'")
                }
            }
        }

        private fun parseArray(): JsonValue.JsonArr {
            expectChar('[')
            skipWhitespace()
            val items = mutableListOf<JsonValue>()
            if (peekChar() == ']') {
                position++
                return JsonValue.JsonArr(items)
            }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                when (val sep = peekChar()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return JsonValue.JsonArr(items)
                    }
                    else -> throw BrandThemeException("Expected ',' or ']' in JSON array at index $position, got '${sep ?: "EOF"}'")
                }
            }
        }

        private fun parseString(): String {
            expectChar('"')
            val out = StringBuilder()
            while (position < source.length) {
                when (val ch = source[position++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (isAtEnd()) {
                            throw BrandThemeException("Unterminated escape sequence in JSON string")
                        }
                        when (val esc = source[position++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (position + 4 > source.length) {
                                    throw BrandThemeException("Incomplete unicode escape in JSON string at index $position")
                                }
                                val hex = source.substring(position, position + 4)
                                val code = hex.toIntOrNull(16)
                                    ?: throw BrandThemeException("Invalid unicode escape \\u$hex in JSON string")
                                out.append(code.toChar())
                                position += 4
                            }
                            else -> throw BrandThemeException("Invalid escape character '\\$esc' in JSON string")
                        }
                    }
                    in '\u0000'..'\u001F' -> throw BrandThemeException("Unescaped control character in JSON string at index $position")
                    else -> out.append(ch)
                }
            }
            throw BrandThemeException("Unterminated JSON string")
        }

        private fun parseNumber(): JsonValue.JsonNum {
            val start = position
            if (peekChar() == '-') position++
            if (isAtEnd() || source[position] !in '0'..'9') {
                throw BrandThemeException("Invalid JSON number at index $start")
            }
            if (source[position] == '0') {
                position++
            } else {
                while (!isAtEnd() && source[position] in '0'..'9') {
                    position++
                }
            }
            if (!isAtEnd() && source[position] == '.') {
                position++
                if (isAtEnd() || source[position] !in '0'..'9') {
                    throw BrandThemeException("Invalid fractional number in JSON at index $start")
                }
                while (!isAtEnd() && source[position] in '0'..'9') {
                    position++
                }
            }
            if (!isAtEnd() && (source[position] == 'e' || source[position] == 'E')) {
                position++
                if (!isAtEnd() && (source[position] == '+' || source[position] == '-')) {
                    position++
                }
                if (isAtEnd() || source[position] !in '0'..'9') {
                    throw BrandThemeException("Invalid exponent in JSON number at index $start")
                }
                while (!isAtEnd() && source[position] in '0'..'9') {
                    position++
                }
            }
            val token = source.substring(start, position)
            val parsed = token.toDoubleOrNull()
                ?: throw BrandThemeException("Numeric literal out of range in JSON: $token")
            if (!parsed.isFinite()) {
                throw BrandThemeException("Non-finite number in JSON: $token")
            }
            return JsonValue.JsonNum(parsed)
        }

        private fun expectKeyword(keyword: String) {
            if (!source.startsWith(keyword, position)) {
                throw BrandThemeException("Expected '$keyword' in JSON at index $position")
            }
            position += keyword.length
        }

        private fun expectChar(expected: Char) {
            if (isAtEnd() || source[position] != expected) {
                throw BrandThemeException("Expected '$expected' in JSON at index $position")
            }
            position++
        }

        private fun peekChar(): Char? = if (isAtEnd()) null else source[position]
    }
}

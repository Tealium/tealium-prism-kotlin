package com.tealium.prism.core.internal.data

import com.tealium.prism.core.api.data.JsonPath.Component
import com.tealium.prism.core.api.data.JsonPathParseException
import com.tealium.prism.core.api.data.JsonPathSyntaxException
import com.tealium.prism.core.api.data.UnexpectedEndOfInputException

class JsonPathParser(private val path: String) {

    private var index: Int = 0

    /**
     * Returns true if there are still more characters left to consume.
     */
    private fun hasNext(): Boolean = index < path.length

    /**
     * Returns the current character without consuming it.
     */
    @Throws(UnexpectedEndOfInputException::class)
    private fun peek(): Char {
        if (!hasNext()) {
            throw UnexpectedEndOfInputException("Unexpected end of input.")
        }
        return path[index]
    }

    /**
     * Consumes and returns the current character.
     */
    @Throws(UnexpectedEndOfInputException::class)
    private fun next(): Char {
        return peek().also { index++ }
    }

    /**
     * Consumes the expected character or throws.
     */
    @Throws(
        UnexpectedEndOfInputException::class,
        JsonPathSyntaxException::class
    )
    private fun expect(expected: Char) {
        val c = next()
        if (c != expected) {
            throw JsonPathSyntaxException(index - 1, "Expected '$expected' but found '$c'")
        }
    }

    /**
     * Attempts to parse [path] as a JsonPath, returning a list of [Component]s. This method puts no
     * restrictions on which type of [Component] is first in the [path]
     *
     * This method will throw a [JsonPathParseException] when an error was detected when parsing. This
     * could happen for a variety of reasons, e.g. reaching the end-of-input when expecting more tokens,
     * or a syntax error like unclosed quotation marks etc
     *
     * @throws JsonPathParseException when encountering an issue when parsing the [path]
     */
    @Throws(JsonPathParseException::class)
    fun parseComponents(): List<Component> {
        val components = mutableListOf<Component>()

        do { // always consume something in case of empty
            val c = peek()
            if (c == '[') {
                next() // consume '['
                components.add(parseBracketComponent())
            } else if (c == '.' && components.isNotEmpty()) {
                next() // consume '.'
                components.add(parseKeyComponent())
            } else if (components.isEmpty()) {
                // handle first component being a key
                components.add(parseKeyComponent())
            } else {
                throw JsonPathSyntaxException(index, "Missing a separator between components")
            }
        } while (hasNext())

        return components.toList()
    }

    /**
     * Parses the next component as a bracketed component: either a quoted key or a numeric index.
     * Assumes '[' has already been consumed.
     */
    private fun parseBracketComponent(): Component {
        val component: Component = when (val c = peek()) {
            '"', '\'' -> parseQuotedKeyComponent(next())
            else -> parseIndexComponent()
        }

        expect(']') // Always consume the closing bracket here

        return component
    }

    /**
     * Parses the next component as a bracketed, quoted key. Pass the relevant opening [quote] in
     * order to support either `"` or `'` characters as the delimiter.
     *
     * Assumes that the opening `[` and `"` have already been consumed.
     */
    private fun parseQuotedKeyComponent(quote: Char): Component.Key {
        val start = index
        var key: String

        while (true) {
            when (next()) {
                '\\' -> { // slow path, requires escaping
                    val partialKey = path.substring(start, index - 1)
                    key = continueParsingEscapedQuotedKeyComponent(quote, partialKey)
                    break
                }
                quote -> { // fast path, nothing to escape
                    key = path.substring(start, index - 1)
                    break
                }
            }
        }

        return Component.Key(key)
    }

    /**
     * Continues to parse a quoted key as if it contains a supported escape character.
     *
     * This method assumes that the initial backslash `\` has already been consumed.
     */
    private fun continueParsingEscapedQuotedKeyComponent(quote: Char, partialKey: String): String {
        val sb = StringBuilder(partialKey)
        var c = '\\'
        while (c != quote) {
            when (c) {
                '\\' -> {
                    val e = next()
                    when (e) {
                        '"', '\'', '\\' -> sb.append(e)
                        else -> throw JsonPathSyntaxException(
                            index,
                            "Invalid escape '$e' at index ${index - 1}; expected one of: ' \" \\ "
                        )
                    }
                }
                else -> {
                    sb.append(c)
                }
            }
            c = next()
        }

        return sb.toString()
    }

    /**
     * Parses the next component as a bracketed array index.
     *
     * Assumes that the opening `[` has already been consumed.
     */
    private fun parseIndexComponent(): Component.Index {
        val start = index
        while (peek() != ']') {
            next()
        }
        val numberString = path.substring(start, index)
        val number = numberString.toIntOrNull()
            ?: throw JsonPathSyntaxException(
                start,
                "Invalid array index '$numberString' - must be a valid integer"
            )
        return Component.Index(number)
    }

    /**
     * Parses the next component as an unquoted key (dot-notation).
     */
    @Throws(JsonPathParseException::class)
    private fun parseKeyComponent(): Component {
        val start = index

        val firstCharacter = next()
        if (!isAllowedFirstDotNotationCharacter(firstCharacter)) {
            throw JsonPathSyntaxException(index - 1, "Invalid character $firstCharacter. Unquoted keys must start with a letter or an underscore")
        }

        while (hasNext()) {
            val c = peek()
            if (c == '.' || c == '[') break
            if (!isAllowedDotNotationCharacter(c)) {
                throw JsonPathSyntaxException(
                    index,
                    "Invalid character $c. Use bracket notation with quotes for non-identifier keys, e.g. [\"...\"]"
                )
            }
            next()
        }

        val key = path.substring(start, index)
        return Component.Key(key)
    }

    companion object {
        /**
         * Checks if an individual Character would be allowed in a dot.notated key.
         *
         * Valid characters are alphanumeric and underscore.
         */
        fun isAllowedDotNotationCharacter(c: Char): Boolean =
            Character.isLetterOrDigit(c) || c == '_'

        /**
         * Checks if an individual Character would be allowed to start a dot.notated key.
         *
         * First characters should only be a letter or an underscore `_`
         */
        fun isAllowedFirstDotNotationCharacter(c: Char) =
            Character.isLetter(c) || c == '_'

        /**
         * Determines whether or not a [Component.Key] can be safely written using `dot.notation` -
         * that is, it does not need to be wrapped in square brackets and quotes: `["..."]`
         */
        fun isDotNotationAllowed(component: Component.Key): Boolean {
            val key = component.key
            return key.isNotEmpty()
                    && isAllowedFirstDotNotationCharacter(key.first())
                    && !key.any { !isAllowedDotNotationCharacter(it) }
        }
    }
}

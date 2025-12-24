package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.JsonPathParseException
import com.tealium.prism.core.internal.utils.format

/**
 * Utility object for injecting dynamic values into templates.
 *
 * For example, given the following template and input context
 *
 * Template:
 * > Hello, {{user.name}}.
 * Context:
 * ```json
 * {
 *   "user": {
 *     "name": "Bill"
 *   }
 * }
 * ```
 * The processed template would output
 * > Hello, Bill.
 *
 * In cases where the value may not be available in the context, a fallback string can be provided
 * to be used instead. So given the following template and input context:
 *
 * Template:
 * > Hello, {{user.name || beloved user}}.
 * Context:
 * ```json
 * {
 *   "user": {
 *     "age": 42
 *   }
 * }
 * ```
 * The processed template would output
 * > Hello, beloved user.
 */
object TemplateProcessor {

    private val handlebarsRegex =
        Regex("""\{\{(.*?)\}\}""")

    /**
     * Processes the input [template] looking for all occurrences of double brace wrapped text: `{{  }}`
     *
     * The format of text inside the braces can be as follows:
     *  - a valid json path style string: e.g. `{{container.key}}`
     *  - a valid json path style string with an optional fallback: e.g. `{{container.key || fallback}}`
     *     - in the event that `container.key` is not available in the [context] object, the fallback will be used
     *
     * All occurrences of the templating `{{ }}` will be replaced with either the value from the [context]
     * object according to the json path specified, or the fallback string if provided, else a blank string `""`
     *
     * This method is lenient, meaning that invalid [JsonPath] strings will be ignored, and a fallback
     * or empty string will be resolved instead.
     *
     * Regarding the formatting of different data types.
     *  - [String], [Integer], [Long], [Boolean] all follow their [toString] implementation
     *  - [Double]s will be in a human readable format; non-scientific, and trimmed decimal places
     *  - [DataItem.NULL] or missing values will resolve as empty strings `""`
     *  - [DataObject] and [DataList] will be formatted as JSON, and thus nested values follow
     *  standard JSON formatting (i.e. scientific notation is allowed as well as `null`s)
     *
     * @param template the String to process for `{{ }}` substitution block
     * @param context the [DataObject] to extract values from
     * @return A new string with all substitution blocks replaced
     */
    fun process(template: String, context: DataObject): String =
        template.replace(handlebarsRegex) { matchResult ->
            processMatchResult(matchResult, context) ?: ""
        }

    private fun processMatchResult(match: MatchResult, context: DataObject): String? {
        val template = match.groups[1]?.value
            ?: return ""

        val parts = template.split("||")
            .map { it.trim() }

        val variablePath = parts.first()
        val fallback = parts.getOrNull(1)

        return parseAndExtractValue(variablePath, context, fallback)
    }

    private fun parseAndExtractValue(
        jsonObjectPathString: String,
        context: DataObject,
        fallback: String?
    ): String? {
        val jsonPath = try {
            JsonPath.parseJsonObjectPath(jsonObjectPathString)
        } catch (_: JsonPathParseException) {
            return fallback
        }

        return context.extract(jsonPath)?.format()
            ?: fallback
    }
}
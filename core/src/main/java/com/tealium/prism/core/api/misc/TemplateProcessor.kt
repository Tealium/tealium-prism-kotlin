package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.data.DataItem.Companion.NULL
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.JsonPathParseException
import com.tealium.prism.core.internal.misc.TemplateImpl

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
     *  - [Double]s will be in a human-readable format; non-scientific, and trimmed decimal places
     *  - [NULL] or missing values will resolve as empty strings `""`
     *  - [DataObject] and [DataList] will be formatted as JSON, and thus nested values follow
     *  standard JSON formatting (i.e. scientific notation is allowed as well as `null`s)
     *
     * @param template the String to process for `{{ }}` substitution block
     * @param context the [DataObject] to extract values from
     * @return A new string with all substitution blocks replaced
     */
    @JvmStatic
    fun process(template: String, context: DataObject): String =
        compile(template).process(context)

    /**
     * Compiles a reusable, partially processed [Template] from the given [template] text. Any
     * substitutions are discovered ahead of time and can be inspected in [Template.substitutions].
     *
     * The returned object follows the same processing rules as the [TemplateProcessor.process].
     *
     * @param template the String to process for `{{ }}` substitution blocks
     */
    @JvmStatic
    fun compile(template: String): Template {
        val builder = TemplateImpl.Builder()
        var lastIndex = 0

        for (m in handlebarsRegex.findAll(template)) {
            // Append any preceding plain text
            if (m.range.first > lastIndex) {
                builder.text(template.substring(lastIndex, m.range.first))
            }

            val placeholder = m.groupValues[1]
            val parts = placeholder.split("||")
                .map { it.trim() }
            val pathStr = parts.first()
            val fallback = parts.getOrNull(1)

            try {
                val path = JsonPath.parseJsonObjectPath(pathStr)
                builder.substitute(path, fallback)
            } catch (_: JsonPathParseException) {
                if (fallback != null) builder.text(fallback)
            }

            lastIndex = m.range.last + 1
        }

        // Append any remaining trailing text
        if (lastIndex < template.length) {
            builder.text(template.substring(lastIndex))
        }

        return builder.build()
    }

    /**
     * A reusable, pre-processed version of some templated text. Any substitutions are discovered
     * ahead of time and can be inspected in [substitutions].
     *
     * This implementation follows the same processing rules as the [TemplateProcessor].
     *
     * @see TemplateProcessor.compile
     * @see TemplateProcessor.process
     */
    interface Template {

        /**
         * A list of all the substitutions found within the template text. This can be used to determine
         * what, if any, context data needs to be gathered for the call to [process].
         *
         * Substitutions that could not be parsed as [JsonObjectPath] will not appear in this list.
         */
        val substitutions: List<JsonObjectPath>

        /**
         * Processes the input template looking for all occurrences of double brace wrapped text: `{{  }}`
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
         *  - [Double]s will be in a human-readable format; non-scientific, and trimmed decimal places
         *  - [NULL] or missing values will resolve as empty strings `""`
         *  - [DataObject] and [DataList] will be formatted as JSON, and thus nested values follow
         *  standard JSON formatting (i.e. scientific notation is allowed as well as `null`s)
         *
         * @param context the [DataObject] to extract values from
         * @return A new string with all substitution blocks replaced
         */
        fun process(context: DataObject): String
    }
}
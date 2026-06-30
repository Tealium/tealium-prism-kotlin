package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.misc.TemplateProcessor
import com.tealium.prism.core.internal.utils.format

/**
 * Default implementation of [TemplateProcessor.Template].
 *
 * Use the [Builder] to iteratively build up a re-usable template for subsequent processing.
 *
 * This implementation maintains an ordered list of template sections with a simple String join during
 * processing.
 */
class TemplateImpl private constructor(
    private val sections: List<TemplateSection>
) : TemplateProcessor.Template {

    override val substitutions: List<JsonObjectPath> =
        sections.filterIsInstance<TemplateSection.Substitution>()
            .map(TemplateSection.Substitution::path)

    override fun process(context: DataObject): String =
        sections.joinToString("") { it.process(context) }

    /**
     * Representation of the different sections of a template.
     */
    sealed class TemplateSection {
        abstract fun process(context: DataObject): String

        /**
         * A plain text section of a template; no value substitutions will occur during processing
         */
        class Text(val text: String) : TemplateSection() {
            override fun process(context: DataObject): String = text
        }

        /**
         * A section representing a substitutable value that must be resolved from the context
         * during processing.
         */
        class Substitution(
            val path: JsonObjectPath,
            val fallback: String?
        ) : TemplateSection() {
            override fun process(context: DataObject): String =
                context.extract(path)?.format()
                    ?: fallback
                    ?: ""
        }
    }

    /**
     * Basic builder implementation to simplify building whilst parsing some text.
     */
    class Builder() {

        private val sections = mutableListOf<TemplateSection>()

        /**
         * Appends a text-only section.
         *
         * @param text the plain text to add next when resolving the template.
         */
        fun text(text: String) = apply {
            sections.add(TemplateSection.Text(text))
        }

        /**
         * Appends a substitutable section that will need resolving when the template is processed
         *
         * @param path the location to resolve a value from any available context
         * @param fallback optional fallback text in case a value could not be resolved.
         */
        @JvmOverloads
        fun substitute(path: JsonObjectPath, fallback: String? = null) = apply {
            sections.add(TemplateSection.Substitution(path, fallback))
        }

        /**
         * Returns the built [TemplateImpl]
         */
        fun build(): TemplateImpl =
            TemplateImpl(sections.toList())
    }
}
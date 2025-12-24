package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.key
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.path
import com.tealium.prism.core.api.settings.Mappings
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.internal.dispatch.MappingOperation

class MappingsImpl : Mappings {
    private val mappings = mutableListOf<BuilderImpl>()

    private class BuilderImpl(
        val destination: ReferenceContainer,
        private var key: ReferenceContainer? = null,
        private var mapTo: ValueContainer? = null
    ) : Mappings.VariableOptions, Mappings.ConstantOptions {
        private var filter: ValueContainer? = null

        override fun ifValueEquals(value: String): Mappings.VariableOptions =
            apply { filter = ValueContainer(value) }

        override fun ifValueEquals(path: JsonObjectPath, value: String) =
            ifValueEquals(path(path), value)

        override fun ifValueEquals(key: String, value: String): Mappings.ConstantOptions =
            ifValueEquals(key(key), value)

        private fun ifValueEquals(reference: ReferenceContainer, value: String) = apply {
            this.key = reference
            this.filter = ValueContainer(value)
        }

        fun build(): MappingOperation {
            return TransformationOperation(destination, MappingParameters(key, filter, mapTo))
        }
    }

    override fun from(path: JsonObjectPath, destination: JsonObjectPath): Mappings.VariableOptions =
        from(path(path), path(destination))

    override fun from(key: String, destination: String): Mappings.VariableOptions =
        from(key(key), key(destination))

    override fun from(path: JsonObjectPath, destination: String): Mappings.VariableOptions =
        from(path(path), key(destination))

    override fun from(key: String, destination: JsonObjectPath): Mappings.VariableOptions =
        from(key(key), path(destination))

    private fun from(source: ReferenceContainer, destination: ReferenceContainer): Mappings.VariableOptions {
        val builder = BuilderImpl(destination, key = source)
        mappings.add(builder)
        return builder
    }

    override fun constant(value: String, destination: JsonObjectPath): Mappings.ConstantOptions =
        constant(value, path(destination))

    override fun constant(value: String, destination: String): Mappings.ConstantOptions =
        constant(value, key(destination))

    private fun constant(value: String, destination: ReferenceContainer): Mappings.ConstantOptions {
        val builder = BuilderImpl(destination, mapTo = ValueContainer(value))
        mappings.add(builder)
        return builder
    }

    override fun keep(key: String): Mappings.VariableOptions =
        keep(key(key))

    override fun keep(path: JsonObjectPath): Mappings.VariableOptions =
        keep(path(path))

    private fun keep(reference: ReferenceContainer) =
        from(reference, reference)

    fun build(): List<MappingOperation> {
        return mappings.map(BuilderImpl::build)
    }
}
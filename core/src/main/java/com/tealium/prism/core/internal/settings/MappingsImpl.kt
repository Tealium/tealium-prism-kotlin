package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.settings.Mappings
import com.tealium.prism.core.api.settings.ValueContainer
import com.tealium.prism.core.api.settings.VariableAccessor
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.internal.dispatch.MappingOperation

class MappingsImpl : Mappings {
    private val mappings = mutableListOf<BuilderImpl>()

    private class BuilderImpl(
        val destination: VariableAccessor,
        private var key: VariableAccessor? = null,
        private var mapTo: ValueContainer? = null
    ) : Mappings.VariableOptions, Mappings.ConstantOptions {
        private var filter: ValueContainer? = null

        override fun ifValueEquals(value: String): Mappings.VariableOptions =
            apply { filter = ValueContainer(value) }

        override fun ifValueEquals(key: VariableAccessor, value: String) =
            apply {
                this.key = key
                this.filter = ValueContainer(value)
            }

        fun build(): MappingOperation {
            return TransformationOperation(destination, MappingParameters(key, filter, mapTo))
        }
    }

    override fun from(key: VariableAccessor, destination: VariableAccessor): Mappings.VariableOptions {
        val builder = BuilderImpl(destination, key = key)
        mappings.add(builder)
        return builder
    }

    override fun constant(value: String, destination: VariableAccessor): Mappings.ConstantOptions {
        val builder = BuilderImpl(destination, mapTo = ValueContainer(value))
        mappings.add(builder)
        return builder
    }

    fun build(): List<MappingOperation> {
        return mappings.map { it.build() }
    }
}
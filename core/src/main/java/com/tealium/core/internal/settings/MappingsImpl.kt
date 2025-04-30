package com.tealium.core.internal.settings

import com.tealium.core.api.settings.Mappings
import com.tealium.core.api.settings.ValueContainer
import com.tealium.core.api.settings.VariableAccessor
import com.tealium.core.api.settings.json.MappingParameters
import com.tealium.core.api.settings.json.TransformationOperation

class MappingsImpl : Mappings {
    private val mappings = mutableListOf<BuilderImpl>()

    private class BuilderImpl(
        val from: VariableAccessor,
        val to: VariableAccessor
    ) : Mappings.Builder {
        private var filter: ValueContainer? = null
        private var mapTo: ValueContainer? = null

        override fun ifValueEquals(value: String): Mappings.Builder = apply { filter = ValueContainer(value) }

        override fun mapTo(value: String): Mappings.Builder = apply { mapTo = ValueContainer(value) }

        fun build(): TransformationOperation<MappingParameters> {
            return TransformationOperation(to, MappingParameters(from, filter, mapTo))
        }
    }

    override fun from(key: VariableAccessor, destination: VariableAccessor): Mappings.Builder {
        val builder = BuilderImpl(key, destination)
        mappings.add(builder)
        return builder
    }

    fun build(): List<TransformationOperation<MappingParameters>> {
        return mappings.map { it.build() }
    }
}
package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.key
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.path
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.settings.MappingParameters
import com.tealium.prism.core.api.settings.Mappings
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.internal.dispatch.MappingOperation

class MappingsImpl : Mappings {
    private val mappings = mutableListOf<BuilderImpl>()

    private class BuilderImpl(
        val destination: ReferenceContainer,
        private var key: ReferenceContainer? = null,
        private var mapTo: ValueContainer? = null
    ) : Mappings.VariableOptions, Mappings.ConstantOptions, Mappings.CommandOptions {
        private var filter: ValueContainer? = null

        override fun ifValueEquals(value: String) {
            filter = ValueContainer(value)
        }

        override fun ifValueEquals(path: JsonObjectPath, value: String) =
            ifValueEquals(path(path), value)

        override fun ifValueEquals(key: String, value: String) =
            ifValueEquals(key(key), value)

        private fun ifValueEquals(reference: ReferenceContainer, value: String) {
            this.key = reference
            this.filter = ValueContainer(value)
        }

        override fun forAllEvents() =
            ifValueEquals(key(Dispatch.Keys.TEALIUM_EVENT_TYPE), DispatchType.Event.name)

        override fun forAllViews() =
            ifValueEquals(key(Dispatch.Keys.TEALIUM_EVENT_TYPE), DispatchType.View.name)

        fun build(): MappingOperation {
            return TransformationOperation(destination, MappingParameters(key, filter, mapTo))
        }
    }

    override fun mapFrom(path: JsonObjectPath, destination: JsonObjectPath): Mappings.VariableOptions =
        mapFrom(path(path), path(destination))

    override fun mapFrom(key: String, destination: String): Mappings.VariableOptions =
        mapFrom(key(key), key(destination))

    override fun mapFrom(path: JsonObjectPath, destination: String): Mappings.VariableOptions =
        mapFrom(path(path), key(destination))

    override fun mapFrom(key: String, destination: JsonObjectPath): Mappings.VariableOptions =
        mapFrom(key(key), path(destination))

    private fun mapFrom(
        source: ReferenceContainer,
        destination: ReferenceContainer
    ): Mappings.VariableOptions {
        val builder = BuilderImpl(destination, key = source)
        mappings.add(builder)
        return builder
    }

    override fun mapConstant(value: String, destination: JsonObjectPath): Mappings.ConstantOptions =
        mapConstant(value, path(destination))

    override fun mapConstant(value: String, destination: String): Mappings.ConstantOptions =
        mapConstant(value, key(destination))

    private fun mapConstant(value: String, destination: ReferenceContainer): Mappings.ConstantOptions {
        val builder = BuilderImpl(destination, mapTo = ValueContainer(value))
        mappings.add(builder)
        return builder
    }

    override fun keep(key: String): Mappings.VariableOptions =
        keep(key(key))

    override fun keep(path: JsonObjectPath): Mappings.VariableOptions =
        keep(path(path))

    private fun keep(reference: ReferenceContainer) =
        mapFrom(reference, reference)

    override fun mapCommand(name: String): Mappings.CommandOptions {
        val builder = BuilderImpl(key(Dispatch.Keys.COMMAND_NAME), mapTo = ValueContainer(name))
        mappings.add(builder)
        return builder
    }

    override fun build(): List<MappingOperation> {
        return mappings.map(BuilderImpl::build)
    }
}

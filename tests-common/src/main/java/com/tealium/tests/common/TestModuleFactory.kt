package com.tealium.tests.common

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext

class TestModuleFactory(
    override val moduleType: String,
    private val config: List<DataObject> = emptyList(),
    private val canBeDisabled: Boolean = true,
    override val allowsMultipleInstances: Boolean = false,
    private val creator: (String, TealiumContext, DataObject) -> Module? = { _, _, _ -> null }
) : ModuleFactory {

    override fun create(
        moduleId: String,
        context: TealiumContext,
        configuration: DataObject
    ): Module? =
        creator.invoke(moduleId, context, configuration)

    override fun getEnforcedSettings(): List<DataObject> = config

    override fun canBeDisabled(): Boolean = canBeDisabled

    companion object {

        /**
         * Returns a ModuleFactory only allows only a single instance
         */
        fun singleInstance(
            moduleType: String,
            config: DataObject? = null,
            canBeDisabled: Boolean = true,
            creator: (String, TealiumContext, DataObject) -> Module? = { _, _, _ -> null }
        ): ModuleFactory {
            return TestModuleFactory(
                moduleType,
                config?.let { listOf(it) } ?: emptyList(),
                canBeDisabled,
                false,
                creator
            )
        }

        /**
         * Returns a ModuleFactory that returns the given [module] every time.
         */
        fun forModule(
            module: Module,
            config: DataObject? = null,
            canBeDisabled: Boolean = true,
        ) = singleInstance(module.id, config, canBeDisabled, { _, _, _ ->
            module
        })
    }
}
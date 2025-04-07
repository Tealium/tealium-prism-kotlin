package com.tealium.tests.common

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory

class TestModuleFactory(
    override val id: String,
    private val config: DataObject? = null,
    private val canBeDisabled: Boolean = true,
    private val creator: (TealiumContext, DataObject) -> Module? = { _, _ -> null }
) : ModuleFactory {

    /**
     * Returns a ModuleFactory that returns the given [module] every time.
     */
    constructor(
        module: Module,
        config: DataObject? = null,
        canBeDisabled: Boolean = true
    ) : this(module.id, config, canBeDisabled, { _, _ ->
        module
    })

    override fun create(context: TealiumContext, configuration: DataObject): Module? =
        creator.invoke(context, configuration)

    override fun getEnforcedSettings(): DataObject? = config

    override fun canBeDisabled(): Boolean = canBeDisabled
}
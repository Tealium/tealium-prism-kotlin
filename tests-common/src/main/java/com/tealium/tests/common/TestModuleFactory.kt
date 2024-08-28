package com.tealium.tests.common

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory

class TestModuleFactory(
    override val id: String,
    private val config: TealiumBundle? = null,
    private val canBeDisabled: Boolean = true,
    private val creator: (TealiumContext, TealiumBundle) -> Module? = { _, _ -> null }
) : ModuleFactory {

    /**
     * Returns a ModuleFactory that returns the given [module] every time.
     */
    constructor(
        module: Module,
        config: TealiumBundle? = null,
        canBeDisabled: Boolean = true
    ) : this(module.id, config, canBeDisabled, { _, _ ->
        module
    })

    override fun create(context: TealiumContext, settings: TealiumBundle): Module? =
        creator.invoke(context, settings)

    override fun getEnforcedSettings(): TealiumBundle? = config

    override fun canBeDisabled(): Boolean = canBeDisabled
}
package com.tealium.tests.common

import com.tealium.core.TealiumContext
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.settings.ModuleSettings

class TestModuleFactory(
    override val name: String,
    private val creator: (TealiumContext, ModuleSettings) -> Module? = { _, _ -> null }
) : ModuleFactory {

    /**
     * Returns a ModuleFactory that returns the given [module] every time.
     */
    constructor(module: Module) : this(module.name, { _, _ ->
        module
    })

    /**
     * Returns a ModuleFactory that takes its name from the given [module] but creates based on the
     * given [creator].
     */
    constructor(
        module: Module,
        creator: (TealiumContext, ModuleSettings) -> Module?
    ) : this(module.name, creator)

    override fun create(context: TealiumContext, settings: ModuleSettings): Module? =
        creator.invoke(context, settings)
}
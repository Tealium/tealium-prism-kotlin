package com.tealium.tests.common

import com.tealium.core.TealiumContext
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings

class TestModuleFactory(
    override val name: String,
    private val creator: (TealiumContext, ModuleSettings) -> Module? = { _, _ -> null }
) : ModuleFactory {
    override fun create(context: TealiumContext, settings: ModuleSettings): Module? =
        creator.invoke(context, settings)
}
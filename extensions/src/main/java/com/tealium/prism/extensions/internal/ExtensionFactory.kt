package com.tealium.prism.extensions.internal

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder

class ExtensionFactory(
    override val moduleType: String,
    private val creator: (context: TealiumContext, configuration: DataObject) -> Module?,
): ModuleFactory {

    // TODO - other overrides

    override fun getEnforcedSettings(): List<DataObject> {
        return listOf(ModuleSettingsBuilder(moduleType).setEnabled(true).build())
    }

    override fun create(
        moduleId: String,
        context: TealiumContext,
        configuration: DataObject
    ): Module? {
        return creator.invoke(context, configuration)
    }
}
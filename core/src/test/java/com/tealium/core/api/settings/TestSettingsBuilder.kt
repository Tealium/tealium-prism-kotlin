package com.tealium.core.api.settings

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.core.api.settings.modules.MultipleInstancesModuleSettingsBuilder

class TestSettingsBuilder(moduleType: String = "module") :
    ModuleSettingsBuilder<TestSettingsBuilder>(moduleType),
    MultipleInstancesModuleSettingsBuilder<TestSettingsBuilder>
{
    override fun setModuleId(moduleId: String): TestSettingsBuilder =
        setModuleIdInternal(moduleId)

    fun setProperty(key: String, value: Any): TestSettingsBuilder = apply {
        configuration.put(key, DataItem.convert(value))
    }
}
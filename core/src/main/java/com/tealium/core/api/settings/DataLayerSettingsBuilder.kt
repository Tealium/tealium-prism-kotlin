package com.tealium.core.api.settings

import com.tealium.core.api.Modules
import com.tealium.core.api.modules.Module

/**
 * A [ModuleSettingsBuilder] implementation to configure settings relevant to the DataLayer [Module]
 */
class DataLayerSettingsBuilder :
    CollectorSettingsBuilder<DataLayerSettingsBuilder>(Modules.Types.DATA_LAYER)
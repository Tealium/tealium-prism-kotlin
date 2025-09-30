package com.tealium.core.api.settings

import com.tealium.core.api.Modules
import com.tealium.core.api.modules.Module

/**
 * A [ModuleSettingsBuilder] implementation to configure settings relevant to the ConnectivityData [Module]
 */
class ConnectivityDataSettingsBuilder :
    CollectorSettingsBuilder<ConnectivityDataSettingsBuilder>(Modules.Types.CONNECTIVITY_DATA)
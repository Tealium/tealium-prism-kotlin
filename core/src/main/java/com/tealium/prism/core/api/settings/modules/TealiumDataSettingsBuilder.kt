package com.tealium.prism.core.api.settings.modules

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.modules.Module

/**
 * A [ModuleSettingsBuilder] implementation to configure settings relevant to the TealiumData [Module]
 */
class TealiumDataSettingsBuilder :
    CollectorSettingsBuilder<TealiumDataSettingsBuilder>(Modules.Types.TEALIUM_DATA)
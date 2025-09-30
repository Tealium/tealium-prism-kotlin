package com.tealium.core.api.settings

import com.tealium.core.api.Modules
import com.tealium.core.api.modules.Module

/**
 * A [ModuleSettingsBuilder] implementation to configure settings relevant to the DeviceData [Module]
 */
class DeviceDataSettingsBuilder :
    CollectorSettingsBuilder<DeviceDataSettingsBuilder>(Modules.Types.DEVICE_DATA)
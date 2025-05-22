package com.tealium.tests.common

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.settings.ModuleSettingsBuilder
import com.tealium.core.internal.settings.ModuleSettings

/**
 * Utility that builds the modules settings object, and returns only the module configuration DataObject.
 */
fun ModuleSettingsBuilder<*>.buildConfiguration(): DataObject {
    return build().getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
}
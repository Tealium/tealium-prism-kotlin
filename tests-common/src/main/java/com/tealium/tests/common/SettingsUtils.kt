package com.tealium.tests.common

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings

/**
 * Utility that builds the modules settings object, and returns only the module configuration DataObject.
 */
fun ModuleSettingsBuilder<*>.buildConfiguration(): DataObject {
    return build().getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
}

/**
 * One module entry of a [settingsPass]: the module type doubles as the module id.
 */
data class ModuleSettingRow(
    val moduleType: String,
    val order: Int,
    val enabled: Boolean = true
)

/**
 * Builds the [SdkSettings] of a single settings pass out of the given module [rows], so a test can
 * drive `ModuleManager.updateModuleSettings` with an explicit module order and enabled state.
 */
fun settingsPass(vararg rows: ModuleSettingRow): SdkSettings =
    SdkSettings(
        modules = rows.associate { row ->
            row.moduleType to ModuleSettings(
                moduleType = row.moduleType,
                order = row.order,
                enabled = row.enabled
            )
        }
    )

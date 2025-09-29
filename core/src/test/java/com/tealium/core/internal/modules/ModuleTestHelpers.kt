package com.tealium.core.internal.modules

import com.tealium.core.api.modules.Module
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.api.settings.TestSettingsBuilder
import com.tealium.core.internal.settings.ModuleSettings
import com.tealium.core.internal.settings.SdkSettings
import java.util.concurrent.atomic.AtomicInteger

private fun enableDisableModuleSettings(
    moduleId: String,
    moduleType: String = moduleId,
    enabled: Boolean
): SdkSettings = SdkSettings(
    modules = mapOf(
        moduleId to ModuleSettings.Converter.convert(
            TestSettingsBuilder(moduleType)
                .setModuleId(moduleId)
                .setEnabled(enabled)
                .build().asDataItem()
        )!!
    )
)

/**
 * Returns an [SdkSettings] with a Modules Settings DataObject, with the [ModuleSettings.KEY_ENABLED]
 * set to `true`
 */
fun enableModuleSettings(moduleId: String, moduleType: String = moduleId): SdkSettings =
    enableDisableModuleSettings(moduleId, moduleType, true)

/**
 * Returns an [SdkSettings] with a Modules Settings DataObject, with the [ModuleSettings.KEY_ENABLED]
 * set to `true`
 */
fun disableModuleSettings(moduleId: String, moduleType: String = moduleId): SdkSettings =
    enableDisableModuleSettings(moduleId, moduleType, false)

/**
 * Test Module implementation with an observable property
 */
class ModuleWithObservable(
    val subject: Subject<Int>,
    override val id: String,
    override val version: String = "0.0.0"
) : Module {
    var counter = AtomicInteger(0)
}
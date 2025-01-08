package com.tealium.core.internal.modules

import com.tealium.core.api.modules.Module
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.api.settings.ModuleSettingsBuilder
import com.tealium.core.internal.settings.SdkSettings
import java.util.concurrent.atomic.AtomicInteger

private fun enableDisableModuleSettings(name: String, enabled: Boolean) : SdkSettings = SdkSettings(
    mapOf(
        name to ModuleSettingsBuilder()
            .setEnabled(enabled)
            .build()
    )
)

/**
 * Returns an [SdkSettings] with a Modules Settings DataObject, with the [ModuleSettingsBuilder.KEY_ENABLED]
 * set to `true`
 */
fun enableModuleSettings(name: String): SdkSettings = enableDisableModuleSettings(name, true)

/**
 * Returns an [SdkSettings] with a Modules Settings DataObject, with the [ModuleSettingsBuilder.KEY_ENABLED]
 * set to `true`
 */
fun disableModuleSettings(name: String): SdkSettings = enableDisableModuleSettings(name, false)

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
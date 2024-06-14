package com.tealium.core.internal

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.internal.settings.ModuleSettingsImpl
import com.tealium.core.internal.settings.CoreSettings

data class SdkSettings(
    val moduleSettings: Map<String, ModuleSettings> = emptyMap()
) : TealiumSerializable {

    val coreSettings: CoreSettings
        get() = moduleSettings[CoreSettings.moduleName]?.let {
            CoreSettings.fromBundle(it.bundle)
        } ?: CoreSettings()

    override fun asTealiumValue(): TealiumValue {
        return TealiumBundle.create {
            for (settings in moduleSettings) {
                put(settings.key, settings.value.bundle)
            }

        }.asTealiumValue()
    }

    object Deserializer : TealiumDeserializable<SdkSettings> {
        override fun deserialize(value: TealiumValue): SdkSettings? {
            val bundle = value.getBundle() ?: return null

            val modulesSettings: Map<String, ModuleSettings> = bundle.associate { entry ->
                val entryBundle = entry.value.getBundle()
                val enabled = entryBundle?.getBoolean("enabled")
                entry.key to ModuleSettingsImpl(enabled ?: true, entryBundle ?: TealiumBundle.EMPTY_BUNDLE)
            }

            return SdkSettings(
                moduleSettings = modulesSettings,
            )
        }
    }
}
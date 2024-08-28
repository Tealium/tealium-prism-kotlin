package com.tealium.core.internal.settings

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.data.TealiumValue
import com.tealium.core.api.settings.CoreSettings

data class SdkSettings(
    val moduleSettings: Map<String, TealiumBundle> = emptyMap()
) : TealiumSerializable {

    val coreSettings: CoreSettings
        get() = moduleSettings[CoreSettingsImpl.MODULE_NAME]?.let {
            CoreSettingsImpl.fromBundle(it)
        } ?: CoreSettingsImpl()

    override fun asTealiumValue(): TealiumValue {
        return TealiumBundle.create {
            for (settings in moduleSettings) {
                put(settings.key, settings.value)
            }

        }.asTealiumValue()
    }

    object Deserializer : TealiumDeserializable<SdkSettings> {
        override fun deserialize(value: TealiumValue): SdkSettings? {
            val bundle = value.getBundle() ?: return null

            val modulesSettings: Map<String, TealiumBundle> = bundle.associate { entry ->
                val entryBundle = entry.value.getBundle()
                entry.key to (entryBundle ?: TealiumBundle.EMPTY_BUNDLE)
            }

            return SdkSettings(
                moduleSettings = modulesSettings,
            )
        }
    }
}
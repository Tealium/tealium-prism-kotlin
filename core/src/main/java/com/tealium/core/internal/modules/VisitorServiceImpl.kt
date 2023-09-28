package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.CoreSettings
import com.tealium.core.api.DataStore
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings
import com.tealium.core.api.VisitorProfile
import com.tealium.core.api.VisitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

class VisitorServiceImpl(
    private var settings: VisitorServiceSettings,
    private val dataStore: DataStore
) : VisitorService, Module {

    private val visitorIdFlow: MutableStateFlow<String> =
        MutableStateFlow(generateVisitorId()) // TODO load from storage
    private val visitorProfileFlow: MutableStateFlow<VisitorProfile> = MutableStateFlow(
        VisitorProfile() // TODO load latest
    )

    override val visitorId: String
        get() = visitorIdFlow.value

    override val onVisitorIdUpdated: StateFlow<String>
        get() = visitorIdFlow.asStateFlow()


    override val visitorProfile: VisitorProfile
        get() = visitorProfileFlow.value

    override val onVisitorProfileUpdated: StateFlow<VisitorProfile>
        get() = visitorProfileFlow.asStateFlow()

    override fun resetVisitorId() {
        val newId = generateVisitorId()


//        _visitorId.update(generateVisitorId())
    }

    override fun clearStoredVisitorIds() {
        // TODO()
    }

    override fun updateSettings(coreSettings: CoreSettings, moduleSettings: ModuleSettings) {
        settings = VisitorServiceSettings.fromModuleSettings(moduleSettings)
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object {
        private const val moduleName = "VisitorService"

        private fun generateVisitorId(uuid: UUID = UUID.randomUUID()): String {
            return uuid.toString().replace("-", "")
        }
    }

    object Factory : ModuleFactory {
        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module {
            return VisitorServiceImpl(
                VisitorServiceSettings.fromModuleSettings(settings),
                context.storageProvider.getModuleStore(this)
            )
        }
    }
}

class VisitorServiceSettings(
    val urlTemplate: String = DEFAULT_VISITOR_SERVICE_TEMPLATE,
    val profile: String? = null,
    val refreshIntervalSeconds: Long = DEFAULT_REFRESH_INTERVAL_SECONDS
) {

    companion object {
        const val VISITOR_PROFILE_FILENAME = "visitor_profile.json"
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 300L

        // url replacements
        const val PLACEHOLDER_ACCOUNT = "{{account}}"
        const val PLACEHOLDER_PROFILE = "{{profile}}"
        const val PLACEHOLDER_VISITOR_ID = "{{visitorId}}"

        const val DEFAULT_VISITOR_SERVICE_TEMPLATE =
            "https://visitor-service.tealiumiq.com/$PLACEHOLDER_ACCOUNT/$PLACEHOLDER_PROFILE/$PLACEHOLDER_VISITOR_ID"

        const val VISITOR_SERVICE_OVERRIDE_URL = "override_visitor_service_url"
        const val VISITOR_SERVICE_OVERRIDE_PROFILE = "override_visitor_service_profile"
        const val VISITOR_SERVICE_REFRESH_INTERVAL = "override_visitor_refresh_interval"

        fun fromModuleSettings(settings: ModuleSettings): VisitorServiceSettings {
            return VisitorServiceSettings(
                settings.settings[VISITOR_SERVICE_OVERRIDE_URL] as? String
                    ?: DEFAULT_VISITOR_SERVICE_TEMPLATE,
                settings.settings[VISITOR_SERVICE_OVERRIDE_PROFILE] as? String,
                settings.settings[VISITOR_SERVICE_REFRESH_INTERVAL] as? Long
                    ?: DEFAULT_REFRESH_INTERVAL_SECONDS,
            )
        }
    }
}
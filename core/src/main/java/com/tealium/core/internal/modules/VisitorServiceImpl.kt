package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.DataStore
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.VisitorProfile
import com.tealium.core.api.VisitorService
import com.tealium.core.api.listeners.Subscribable
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.internal.observables.Observable
import java.util.UUID

class VisitorServiceWrapper(
    private val moduleProxy: ModuleProxy<VisitorServiceImpl>
) : VisitorService {

    constructor(
        moduleManager: ModuleManager
    ) : this(ModuleProxy(VisitorServiceImpl::class.java, moduleManager))

    override val onVisitorIdUpdated: Subscribable<String>
        get() = moduleProxy.getModule()
            .flatMap { it.onVisitorIdUpdated }
    override val onVisitorProfileUpdated: Subscribable<VisitorProfile>
        get() = moduleProxy.getModule()
            .flatMap { it.onVisitorProfileUpdated }

    override fun resetVisitorId() {
        moduleProxy.getModule { visitorService ->
            visitorService?.resetVisitorId()
        }
    }

    override fun clearStoredVisitorIds() {
        moduleProxy.getModule { visitorService ->
            visitorService?.clearStoredVisitorIds()
        }
    }
}

class VisitorServiceImpl(
    private var settings: VisitorServiceSettings,
    private val dataStore: DataStore
) : Module {

    private val visitorIdSubject: StateSubject<String> =
        Observables.stateSubject(generateVisitorId()) // TODO load from storage
    private val visitorProfileFlow: StateSubject<VisitorProfile> = Observables.stateSubject(
        VisitorProfile() // TODO load latest
    )

    val onVisitorIdUpdated: Observable<String>
        get() = visitorIdSubject.asObservable()

    val onVisitorProfileUpdated: Observable<VisitorProfile>
        get() = visitorProfileFlow.asObservable()

    fun resetVisitorId() {
        val newId = generateVisitorId()

//        _visitorId.update(generateVisitorId())
    }

    fun clearStoredVisitorIds() {
        // TODO()
    }

    override fun updateSettings(moduleSettings: ModuleSettings): Module? {
        settings = VisitorServiceSettings.fromModuleSettings(moduleSettings)
        return this // TODO or null if required settings are not available
    }

    override val name: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object {
        const val moduleName = "VisitorService"

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
    val enabled: Boolean = true,
    val dependencies: List<Any> = emptyList(),
    val urlTemplate: String = DEFAULT_VISITOR_SERVICE_TEMPLATE,
    val profile: String? = null,
    val refreshIntervalSeconds: Long = DEFAULT_REFRESH_INTERVAL_SECONDS
) {

    companion object {
        const val KEY_VISITOR_SERVICE_SETTINGS = "visitor_service"

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
            val dependencies = settings.dependencies
            val url = settings.bundle.getString(VISITOR_SERVICE_OVERRIDE_URL)
                ?: DEFAULT_VISITOR_SERVICE_TEMPLATE
            val profile = settings.bundle.getString(VISITOR_SERVICE_OVERRIDE_PROFILE)
            val refreshInterval = settings.bundle.getLong(VISITOR_SERVICE_REFRESH_INTERVAL)
                ?: DEFAULT_REFRESH_INTERVAL_SECONDS
            return VisitorServiceSettings(
                urlTemplate = url,
                profile = profile,
                refreshIntervalSeconds = refreshInterval,
                dependencies = dependencies
            )
        }
    }
}
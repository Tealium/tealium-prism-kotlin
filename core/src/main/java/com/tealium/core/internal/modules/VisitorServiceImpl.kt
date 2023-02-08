package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.Modules
import com.tealium.core.Tealium
import com.tealium.core.TealiumContext
import com.tealium.core.api.CoreSettings
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleSettings
import com.tealium.core.api.VisitorProfile
import com.tealium.core.api.VisitorService
import com.tealium.core.api.data.MutableObservableProperty
import com.tealium.core.api.data.ObservableProperty
import java.util.*

class VisitorServiceImpl(
    private var settings: VisitorServiceSettings,
    private val _visitorId: MutableObservableProperty<String, VisitorService.VisitorIdUpdatedListener>,
    private val _visitorProfile: MutableObservableProperty<VisitorProfile, VisitorService.VisitorProfileUpdatedListener>
) : VisitorService, Module {

    private constructor(
        context: TealiumContext,
        settings: VisitorServiceSettings
    ) : this(
        settings,
        context.observables.createBufferedProperty(
            generateVisitorId(),
            size = 3,
            deliver = { observer, value ->
                observer.onVisitorIdUpdated(value)
            }
        ),
        context.observables.createProperty(
            initial = VisitorProfile(),
            deliver = { observer, value ->
                observer.onVisitorProfileUpdated(value)
            }
        )
    )

//    private val _someDelegatingProp: MutableObservableProperty<String, VisitorService.VisitorIdUpdatedListener> =
//        context.observables.Builder<String, VisitorService.VisitorIdUpdatedListener>(
//            generateVisitorId()
//        ).onUpdate { observer, value ->
//            observer.onVisitorIdUpdated(value)
//        }.build()


    //    private val _visitorId = object :
//        MutableObservablePropertyImpl<String, VisitorService.VisitorIdUpdatedListener>(
//            generateVisitorId(), // TODO - read from disk
//        ) {
//        override fun deliver(observer: VisitorService.VisitorIdUpdatedListener, value: String) {
//            observer.onVisitorIdUpdated(value)
//        }
//    }
//    private val _visitorId: MutableObservableProperty<String, VisitorService.VisitorIdUpdatedListener> =
//        context.observables.createProperty(generateVisitorId()) { observer, value ->
//            observer.onVisitorIdUpdated(value)
//        }

    //    private val _visitorId: MutableObservableProperty<String, VisitorService.VisitorIdUpdatedListener> =
//        context.observables.createBufferedProperty(
//            generateVisitorId(),
//            size = 3
//        ) { observer, value ->
//            observer.onVisitorIdUpdated(value)
//        }
    override val visitorId: ObservableProperty<String, VisitorService.VisitorIdUpdatedListener>
        get() = _visitorId

    //    private val _visitorProfile = object :
//        MutableObservablePropertyImpl<VisitorProfile, VisitorService.VisitorProfileUpdatedListener>(
//            VisitorProfile()
//        ) {
//        override fun deliver(
//            observer: VisitorService.VisitorProfileUpdatedListener,
//            value: VisitorProfile
//        ) {
//            observer.onVisitorProfileUpdated(value)
//        }
//    }
    override val visitorProfile: ObservableProperty<VisitorProfile, VisitorService.VisitorProfileUpdatedListener>
        get() = _visitorProfile

    override fun resetVisitorId() {
        _visitorId.update(generateVisitorId())
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
        get() = moduleVersion

    companion object {
        private const val moduleName = "VisitorService"
        private const val moduleVersion = BuildConfig.TEALIUM_LIBRARY_VERSION

        private fun generateVisitorId(uuid: UUID = UUID.randomUUID()): String {
            return uuid.toString().replace("-", "")
        }
    }

    object Factory : ModuleFactory {
        override val name: String
            get() = moduleName

        override fun create(context: TealiumContext, settings: ModuleSettings): Module {
            return VisitorServiceImpl(context, VisitorServiceSettings.fromModuleSettings(settings))
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


// TODO - move elsewhere
val Tealium.visitorService: VisitorService?
    get() = modules.getModuleOfType(VisitorService::class.java)

// TODO - move elsewhere
val Modules.VisitorService: ModuleFactory
    get() = VisitorServiceImpl.Factory
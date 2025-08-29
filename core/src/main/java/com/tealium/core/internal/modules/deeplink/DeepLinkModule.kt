package com.tealium.core.internal.modules.deeplink

import android.content.Intent
import android.net.Uri
import com.tealium.core.BuildConfig
import com.tealium.core.api.Modules
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.logIfErrorEnabled
import com.tealium.core.api.logger.logIfTraceEnabled
import com.tealium.core.api.logger.logIfWarnEnabled
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.modules.Collector
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleNotEnabledException
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.settings.DeepLinkSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.tracking.Tracker
import com.tealium.core.internal.logger.nonNullMessage
import com.tealium.core.internal.modules.trace.TraceModule

class DeepLinkModule(
    private val dataStore: DataStore,
    private val tracker: Tracker,
    private val moduleManager: ModuleManager,
    private val activities: Observable<ActivityManager.ActivityStatus>,
    private var configuration: DeepLinkModuleConfiguration,
    private val logger: Logger
) : Collector {

    private var disposable: Disposable? = null

    init {
        updateAutomaticTrackingSubscription()
    }

    fun handle(link: Uri, referrer: Uri? = null) {
        if (link.isOpaque) return

        if (configuration.deepLinkTraceEnabled) {
            handleDeepLinkTrace(link)
        }

        handleDeepLink(link, referrer)
    }

    override fun collect(dispatchContext: DispatchContext): DataObject {
        if (dispatchContext.source.isFromModule(this::class.java))
            return DataObject.EMPTY_OBJECT

        return dataStore.getAll()
    }

    private fun getTrace(): TraceModule? =
        moduleManager.getModuleOfType(TraceModule::class.java)

    private fun handleDeepLinkTrace(uri: Uri) {
        val traceId = uri.getQueryParameter(TRACE_ID_QUERY_PARAM)
            ?: return

        val trace = getTrace()
            ?: throw ModuleNotEnabledException("Trace Module is not enabled.")

        if (uri.getQueryParameter(KILL_VISITOR_SESSION) != null) {
            trace.killVisitorSession { result ->
                if (result.status == TrackResult.Status.Accepted) {
                    logger.logIfTraceEnabled(id) {
                        "KillVisitorSession event accepted for dispatch."
                    }
                } else {
                    logger.logIfWarnEnabled(id) {
                        "Failed to kill visitor session: dispatch was dropped"
                    }
                }
            }
        }

        if (uri.getQueryParameter(LEAVE_TRACE_QUERY_PARAM) != null) {
            trace.leave()
        } else {
            trace.join(traceId)
        }
    }

    private fun handleDeepLink(uri: Uri, referrer: Uri?) {
        val dataObject = buildDeepLinkDataObject(uri, referrer)

        dataStore.edit()
            .clear()
            .putAll(dataObject, Expiry.SESSION)
            .commit()

        if (configuration.sendDeepLinkEvent) {
            tracker.track(
                Dispatch.create(DEEP_LINK_EVENT, dataObject = dataObject),
                DispatchContext.Source.module(this::class.java)
            ) { result ->
                if (result.status == TrackResult.Status.Accepted) {
                    logger.logIfTraceEnabled(id) {
                        "DeepLink event accepted for dispatch."
                    }
                } else {
                    logger.logIfWarnEnabled(id) {
                        "Failed to send DeepLink event: dispatch was dropped"
                    }
                }
            }
        }
    }

    private fun buildDeepLinkDataObject(uri: Uri, referrer: Uri?): DataObject {
        val builder = DataObject.Builder()

        if (referrer != null) {
            builder.put(Dispatch.Keys.DEEP_LINK_REFERRER_URL, referrer.toString())
        }

        builder.put(Dispatch.Keys.DEEP_LINK_URL, uri.toString())

        for (name in uri.queryParameterNames) {
            val param = uri.getQueryParameter(name)
                ?: continue

            builder.put("${Dispatch.Keys.DEEP_LINK_QUERY_PREFIX}_$name", param)
        }

        return builder.build()
    }

    private fun onActivityStatus(activityStatus: ActivityManager.ActivityStatus) {
        if (activityStatus.type != ActivityManager.ActivityLifecycleType.Created) return

        val activity = activityStatus.activity
        val intent = activity.intent
        if (Intent.ACTION_VIEW != intent.action) return

        val uri = intent.data ?: return
        val referrer = activity.referrer
            ?: intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri::class.java)
            ?: intent.getParcelableExtra(Intent.EXTRA_REFERRER_NAME, String::class.java)?.let {
                Uri.parse(it)
            }

        try {
            handle(uri, referrer)
        } catch (ex: Exception) {
            logger.logIfErrorEnabled(id) {
                "Failed to handle deep link $uri\nError: ${ex.nonNullMessage()}"
            }
        }
    }

    override fun updateConfiguration(configuration: DataObject): Module {
        this.configuration = DeepLinkModuleConfiguration.fromDataObject(configuration)
        updateAutomaticTrackingSubscription()
        return this
    }

    override fun onShutdown() {
        disposable?.dispose()
    }

    private fun updateAutomaticTrackingSubscription() {
        if (!configuration.automaticDeepLinkTracking) {
            disposable?.dispose()
            disposable = null
            return
        }

        // don't want another subscription
        if (disposable != null) return

        disposable = activities.subscribe(::onActivityStatus)
    }

    override val id: String = Modules.Ids.DEEP_LINK
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object {
        const val TRACE_ID_QUERY_PARAM = Dispatch.Keys.TEALIUM_TRACE_ID
        const val LEAVE_TRACE_QUERY_PARAM = "leave_trace"
        const val KILL_VISITOR_SESSION = "kill_visitor_session"
        const val DEEP_LINK_EVENT = "deep_link"
    }

    class Factory(
        private val enforcedSettings: DataObject? = null
    ) : ModuleFactory {

        constructor(enforcedSettingsBuilder: DeepLinkSettingsBuilder) : this(enforcedSettingsBuilder.build())

        override val id: String = Modules.Ids.DEEP_LINK

        override fun getEnforcedSettings(): DataObject? =
            enforcedSettings

        override fun create(context: TealiumContext, configuration: DataObject): Module? {
            return DeepLinkModule(
                context.storageProvider.getModuleStore(this),
                context.tracker,
                context.moduleManager,
                context.activityManager.activities,
                DeepLinkModuleConfiguration.fromDataObject(configuration),
                context.logger
            )
        }
    }
}

package com.tealium.prism.core.internal.modules.deeplink

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfErrorEnabled
import com.tealium.prism.core.api.logger.logIfTraceEnabled
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.settings.modules.DeepLinkSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.core.internal.logger.nonNullMessage
import com.tealium.prism.core.internal.modules.trace.TraceModule

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
        val referrer = activity.referrer ?: getIntentReferrer(intent)

        try {
            handle(uri, referrer)
        } catch (ex: Exception) {
            logger.logIfErrorEnabled(id) {
                "Failed to handle deep link $uri\nError: ${ex.nonNullMessage()}"
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun getIntentReferrer(intent: Intent) : Uri? {
        val referrer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_REFERRER)
        }

        return referrer ?: intent.getStringExtra(Intent.EXTRA_REFERRER_NAME)?.let {
            Uri.parse(it)
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

    override val id: String = Modules.Types.DEEP_LINK
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object {
        const val TRACE_ID_QUERY_PARAM = Dispatch.Keys.TEALIUM_TRACE_ID
        const val LEAVE_TRACE_QUERY_PARAM = "leave_trace"
        const val KILL_VISITOR_SESSION = "kill_visitor_session"
        const val DEEP_LINK_EVENT = "deep_link"
    }

    class Factory(
        enforcedSettings: DataObject? = null
    ) : ModuleFactory {

        constructor(enforcedSettingsBuilder: DeepLinkSettingsBuilder) : this(enforcedSettingsBuilder.build())

        private val enforcedSettings: List<DataObject> =
            enforcedSettings?.let { listOf(it) } ?: emptyList()

        override val moduleType: String = Modules.Types.DEEP_LINK

        override fun getEnforcedSettings(): List<DataObject> = enforcedSettings

        override fun create(
            moduleId: String,
            context: TealiumContext,
            configuration: DataObject
        ): Module? {
            return DeepLinkModule(
                context.storageProvider.getModuleStore(moduleId),
                context.tracker,
                context.moduleManager,
                context.activityManager.activities,
                DeepLinkModuleConfiguration.fromDataObject(configuration),
                context.logger
            )
        }
    }
}

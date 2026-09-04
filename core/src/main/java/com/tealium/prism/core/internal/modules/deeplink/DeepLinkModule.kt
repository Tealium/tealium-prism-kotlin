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
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.misc.success
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleShutdownException
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.api.settings.modules.DeepLinkSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.core.internal.logger.nonNullMessage
import com.tealium.prism.core.internal.misc.completing
import com.tealium.prism.core.internal.modules.trace.TraceModule

class DeepLinkModule(
    private val dataStore: DataStore,
    private val tracker: Tracker,
    private val moduleManager: ModuleManager,
    private val activities: Observable<ActivityManager.ActivityStatus>,
    private var configuration: DeepLinkModuleConfiguration,
    private val logger: Logger
) : Collector {

    /**
     * The automatic deep link tracking subscription, recreated when
     * [DeepLinkModuleConfiguration.automaticDeepLinkTracking] toggles.
     */
    private var activitySubscription: Disposable? = null

    /** Pending trace resolutions awaiting [ModuleManager.observeModule], failed on shutdown. */
    private val traceSubscriptions: CompositeDisposable = Disposables.composite()

    init {
        updateAutomaticTrackingSubscription()
    }

    fun handle(link: Uri, referrer: Uri? = null, callback: Callback<TealiumResult<Unit>> = Callback {}) {
        if (link.isOpaque) {
            callback.success(Unit)
            return
        }

        val traceId = if (configuration.deepLinkTraceEnabled) {
            link.getQueryParameter(TRACE_ID_QUERY_PARAM)
        } else null

        if (traceId == null) {
            callback.completing { handleDeepLink(link, referrer) }
            return
        }

        onTraceModule { traceResult ->
            callback.completing {
                // join first: the deep_link event must land inside the trace
                applyTrace(traceResult.getOrThrow(), link, traceId)
                handleDeepLink(link, referrer)
            }
        }
    }

    override fun collect(dispatchContext: DispatchContext): DataObject {
        if (dispatchContext.source.isFromModule(this::class.java))
            return DataObject.EMPTY_OBJECT

        return dataStore.getAll()
    }

    /**
     * Resolves the [TraceModule], waiting for it if the module system hasn't finished loading yet.
     *
     * Fails with [ModuleNotEnabledException] when it isn't enabled, or with
     * [ModuleShutdownException] if this module is shut down while the lookup is pending.
     */
    private fun onTraceModule(callback: Callback<TealiumResult<TraceModule>>) {
        // fails the callback if shutdown disposes this before the module resolves
        val pending = Disposables.composite()
        Disposables.subscription {
            // onShutdown() can't tell instance shutdown from settings-disable: claim neither
            callback.failure(ModuleShutdownException(id))
        }.addTo(pending)

        traceSubscriptions.add(pending)
        // already shut down: the guard fired, don't subscribe too
        if (pending.isDisposed) return

        moduleManager.observeModule(TraceModule::class.java).subscribeOnce { trace ->
            traceSubscriptions.remove(pending)
            if (trace == null) {
                callback.failure(ModuleNotEnabledException("Trace Module is not enabled."))
            } else {
                callback.success(trace)
            }
        }.addTo(pending)
    }

    /** Applies the trace requested by [uri]. May throw - callers run this inside [completing]. */
    private fun applyTrace(trace: TraceModule, uri: Uri, traceId: String) {
        if (uri.getQueryParameter(FORCE_END_OF_VISIT) != null) {
            trace.forceEndOfVisit { result ->
                if (result.status == TrackResult.Status.Accepted) {
                    logger.logIfTraceEnabled(id) {
                        "ForceEndOfVisit event accepted for dispatch."
                    }
                } else {
                    logger.logIfWarnEnabled(id) {
                        "Failed to force end of visit: dispatch was dropped"
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
                        "Deep link event accepted for dispatch."
                    }
                } else {
                    logger.logIfWarnEnabled(id) {
                        "Failed to send deep link event: dispatch was dropped"
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

    /**
     * Extracts the deep link from a newly created Activity, or null when it wasn't launched by one.
     */
    private fun ActivityManager.ActivityStatus.toDeepLink(): DeepLink? {
        if (type != ActivityManager.ActivityLifecycleType.Created) return null

        val intent = activity.intent
        if (Intent.ACTION_VIEW != intent.action) return null

        val uri = intent.data ?: return null
        return DeepLink(uri, activity.referrer ?: getIntentReferrer(intent))
    }

    private fun onDeepLink(deepLink: DeepLink) {
        handle(deepLink.uri, deepLink.referrer) { result ->
            result.onFailure { ex ->
                logger.logIfErrorEnabled(id) {
                    "Failed to handle deep link ${deepLink.uri}\nError: ${ex.nonNullMessage()}"
                }
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
        disposeActivitySubscription()
        traceSubscriptions.dispose()
    }

    private fun updateAutomaticTrackingSubscription() {
        if (!configuration.automaticDeepLinkTracking) {
            disposeActivitySubscription()
            return
        }

        // already subscribed
        if (activitySubscription != null) return

        activitySubscription = activities
            .mapNotNull { it.toDeepLink() }
            .subscribe(::onDeepLink)
    }

    private fun disposeActivitySubscription() {
        activitySubscription?.dispose()
        activitySubscription = null
    }

    private data class DeepLink(
        val uri: Uri,
        val referrer: Uri?
    )

    override val id: String = Modules.Types.DEEP_LINK
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION

    companion object {
        const val TRACE_ID_QUERY_PARAM = Dispatch.Keys.TEALIUM_TRACE_ID
        const val LEAVE_TRACE_QUERY_PARAM = "leave_trace"
        const val FORCE_END_OF_VISIT = "kill_visitor_session"
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

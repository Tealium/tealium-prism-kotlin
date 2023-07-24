package com.tealium.mobile

import android.app.Application
import android.util.Log
import com.tealium.core.*
import com.tealium.core.api.ConsentStatus
import com.tealium.core.api.DataLayer
import com.tealium.core.api.Dispatch
import com.tealium.core.internal.modules.Example
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.VisitorService
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.listeners.ConsentStatusUpdatedListener
import com.tealium.core.api.listeners.DispatchDroppedListener
import com.tealium.core.api.listeners.DispatchQueuedListener
import com.tealium.core.api.listeners.DispatchReadyListener
import com.tealium.core.internal.CollectDispatcher
import com.tealium.core.internal.modules.VisitorService
import com.tealium.core.internal.modules.visitorService
import java.lang.Exception

object TealiumHelper :
    DispatchReadyListener,
    DispatchQueuedListener,
    DispatchDroppedListener,
    ConsentStatusUpdatedListener,
//    DataLayer.DataLayerListener
    DataLayer.DataLayerUpdatedListener,
    DataLayer.DataLayerRemovedListener,
    VisitorService.VisitorIdUpdatedListener
{

    override fun onVisitorIdUpdated(visitorId: String) {
        Log.d("Helper", "This Updated VisitorId: $visitorId")
    }

    override fun onDataUpdated(key: String, value: Any) {
        Log.d("Helper", "DataUpdated $key : $value")
    }

    override fun onDataRemoved(keys: Set<String>) {
        Log.d("Helper", "DataRemoved ${keys.joinToString(", ")}")
    }

    fun init(application: Application) {
        val config = TealiumConfig(
            application = application,
            modules = listOf(Example, CollectDispatcher, Modules.VisitorService, Modules.Collect),
            fileName = "tealium-settings.json",
            accountName = "tealiummobile",
            profileName = "android",
            environment = Environment.DEV
        )

        Tealium.create("main", config) {
//            it.events.subscribe(this)
//            it.track("", TealiumDispatchType.Event) {
//                put
//            }
            it.dataLayer.onDataUpdated.subscribe(this)
            it.dataLayer.onDataRemoved.subscribe(this)
            it.dataLayer.onDataRemoved.subscribe {
                it.forEach {
                    Log.d("Lambda", "Removed: key: $it")
                }
            }

            it.dataLayer.put("key", "value")
            it.dataLayer.put("key2", "value2")
            it.dataLayer.remove("key")
            it.dataLayer.remove("key2")

            it.visitorService?.let { vs ->
                val vId = vs.visitorId.get()
                Log.d("VisitorId", "vId = $vId")
                vs.visitorId.subscribe {
                    Log.d("OnMain?", "Executing on ${Thread.currentThread().name}")
                    Log.d("VisitorId", "Updated VisitorId: $it")
                }
                vs.resetVisitorId()
                vs.resetVisitorId()
                vs.resetVisitorId()
                vs.resetVisitorId()
                vs.visitorId.subscribe(this)
                vs.resetVisitorId()
            }

            // do onReady
            Log.d("TealiumHelper", "Tealium is ready")
//            it.consent.consentStatus = ConsentStatus.Consented
//            it.consent.consentStatus = ConsentStatus.NotConsented
//
//            it.track(Dispatch("testEvent", TealiumDispatchType.Event))
//            it.track(
//                Dispatches.event("testEvent")
//                    .putContextData(TealiumBundle.create {
//                        put("key", "value")
//                    })
//                    .build()
//            )
        }
    }

    override fun onDispatchDropped(dispatch: Dispatch) {
        Log.d("TealiumHelper", "Dispatch dropped ${dispatch.payload()}")
    }

    override fun onDispatchQueued(dispatch: Dispatch) {
        Log.d("TealiumHelper", "Dispatch queued ${dispatch.payload()}")
    }

    override fun onDispatchReady(dispatch: Dispatch) {
        Log.d("TealiumHelper", "Dispatch ready ${dispatch.payload()}")
    }

    override fun onConsentStatusUpdated(status: ConsentStatus) {
        Log.d("TealiumHelper", "Status: $status")
    }
}
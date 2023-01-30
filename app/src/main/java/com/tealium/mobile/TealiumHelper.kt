package com.tealium.mobile

import android.app.Application
import android.util.Log
import com.tealium.core.Dispatches
import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.api.ConsentStatus
import com.tealium.core.api.Dispatch
import com.tealium.core.internal.modules.Example
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.listeners.ConsentStatusUpdatedListener
import com.tealium.core.api.listeners.DispatchDroppedListener
import com.tealium.core.api.listeners.DispatchQueuedListener
import com.tealium.core.api.listeners.DispatchReadyListener
import com.tealium.core.internal.CollectDispatcher
import java.lang.Exception

object TealiumHelper :
    DispatchReadyListener,
    DispatchQueuedListener,
    DispatchDroppedListener,
    ConsentStatusUpdatedListener
{
    fun init(application: Application) {
        val config = TealiumConfig(
            application = application,
            modules = listOf(Example, CollectDispatcher),
            fileName = "tealium-settings.json"
        )

        Tealium.create("main", config) {
            it.events.subscribe(this)
//            it.track("", TealiumDispatchType.Event) {
//                put
//            }
            // do onReady
            Log.d("TealiumHelper", "Tealium is ready")
            it.consent.consentStatus = ConsentStatus.Consented
            it.consent.consentStatus = ConsentStatus.NotConsented

            it.track(Dispatch("testEvent", TealiumDispatchType.Event))
            it.track(
                Dispatches.event("testEvent")
                    .putContextData(TealiumBundle.create {
                        put("key", "value")
                    })
                    .build()
            )
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
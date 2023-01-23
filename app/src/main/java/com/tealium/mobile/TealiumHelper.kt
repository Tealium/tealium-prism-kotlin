package com.tealium.mobile

import android.app.Application
import android.util.Log
import com.tealium.core.Dispatches
import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.api.Dispatch
import com.tealium.core.internal.modules.Example
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.data.bundle.TealiumBundle

object TealiumHelper {
    fun init(application: Application) {
        val config = TealiumConfig(
            application = application,
            modules = listOf(Example),
            fileName = "tealium-settings.json"
        )

        Tealium.create("main", config) {

//            it.track("", TealiumDispatchType.Event) {
//                put
//            }
            // do onReady
            Log.d("TealiumHelper", "Tealium is ready")
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
}
package com.tealium.core.internal

import android.util.Log
import com.tealium.core.Tealium
import com.tealium.core.TealiumConfig
import com.tealium.core.TealiumContext
import com.tealium.core.api.*
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.ModuleSettingImpl
import org.json.JSONObject

class TealiumImpl(
    private val config: TealiumConfig,
    private val onReady: Tealium.OnTealiumReady
) : Tealium {

    private val moduleManager = ModuleManagerImpl(
        config.modules, TealiumContext(config.application, this), SdkSettings(
            mapOf("Example" to ModuleSettingImpl())
        )
    )

    private val _trace = TraceManagerImpl()
    private val _deeplink = DeeplinkManagerImpl()
    private val _timedEvents = TimedEventsManagerImpl()
    private val _dataLayer = DataLayerImpl()
    private val _consent = ConsentManagerImpl()

    override val trace: TraceManager
        get() = _trace
    override val deeplink: DeeplinkManager
        get() = _deeplink
    override val timedEvents: TimedEventsManager
        get() = _timedEvents
    override val dataLayer: DataLayer
        get() = _dataLayer
    override val consent: ConsentManager
        get() = _consent

    override fun track(dispatch: Dispatch) {
        val copy = Dispatch(dispatch)
        moduleManager.getModulesOfType(Collector::class.java).forEach {
            copy.addAll(it.collect())
        }
        Log.d("TealiumV2", JSONObject(copy.payload()).toString())
    }

    override fun flushEventQueue() {
        TODO("Not yet implemented")
    }

    fun shutdown() {
        TODO("Not yet implemented")
    }

    init {
        onReady.onReady(this)
    }
}
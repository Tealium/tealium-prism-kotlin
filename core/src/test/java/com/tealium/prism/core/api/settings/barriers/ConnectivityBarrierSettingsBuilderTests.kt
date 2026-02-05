package com.tealium.prism.core.api.settings.barriers

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.internal.network.ConnectivityBarrier
import com.tealium.prism.core.internal.settings.BarrierSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectivityBarrierSettingsBuilderTests {

    @Test
    fun setWifiOnly_Sets_Wifi_Only() {
        val builder = ConnectivityBarrierSettingsBuilder()
        val result = builder.setWifiOnly(true).build()

        val configuration = result.getDataObject(BarrierSettings.Converter.KEY_CONFIGURATION)!!
        assertEquals(true, configuration.getBoolean(ConnectivityBarrier.KEY_WIFI_ONLY))
    }

    @Test
    fun inheritance_From_Base_Builder_Works() {
        val result = ConnectivityBarrierSettingsBuilder()
            .setScopes(setOf(BarrierScope.All))
            .setWifiOnly(true)
            .build()

        val scopes = result.getDataList(BarrierSettings.Converter.KEY_SCOPES)!!
        assertEquals(1, scopes.size)
        assertEquals(BarrierScope.All.value, scopes.getString(0))

        val configuration = result.getDataObject(BarrierSettings.Converter.KEY_CONFIGURATION)!!
        assertEquals(true, configuration.getBoolean(ConnectivityBarrier.KEY_WIFI_ONLY))
    }
}

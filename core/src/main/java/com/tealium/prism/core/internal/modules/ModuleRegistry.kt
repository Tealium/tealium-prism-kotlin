package com.tealium.prism.core.internal.modules

import androidx.annotation.VisibleForTesting
import com.tealium.prism.core.api.Modules.appData
import com.tealium.prism.core.api.Modules.collect
import com.tealium.prism.core.api.Modules.connectivityData
import com.tealium.prism.core.api.Modules.dataLayer
import com.tealium.prism.core.api.Modules.deepLink
import com.tealium.prism.core.api.Modules.deviceData
import com.tealium.prism.core.api.Modules.tealiumData
import com.tealium.prism.core.api.Modules.timeData
import com.tealium.prism.core.api.Modules.trace
import com.tealium.prism.core.api.modules.ModuleFactory
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Internal singleton available to hold all default modules and add any additional ones that may be
 * registered at init time.
 *
 * This instance also supports clearing any additional modules to ensure test reliability
 */
object ModuleRegistry {

    private val _defaultModules: List<ModuleFactory> = listOf(
        appData(null),
        collect(null),
        connectivityData(null),
        dataLayer(),
        deepLink(null),
        deviceData(null),
        tealiumData(),
        timeData(null),
        trace(null)
    )

    private val _additionalModules = CopyOnWriteArrayList<ModuleFactory>()

    fun addDefaultModules(modules: List<ModuleFactory>) {
        _additionalModules.addAll(modules)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun clearAdditionalModules() {
        _additionalModules.clear()
    }

    val defaultModules: List<ModuleFactory>
        get() = _defaultModules + _additionalModules
}
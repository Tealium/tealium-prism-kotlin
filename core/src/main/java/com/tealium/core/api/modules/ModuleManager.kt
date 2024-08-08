package com.tealium.core.api.modules

import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.pubsub.ObservableState

/**
 * The [ModuleManager] is responsible for managing [Module] implementations throughout the [Tealium]
 * instance lifecycle.
 * // TODO - complete
 */
interface ModuleManager {

    /**
     * Observable stream of all [Module] implementations in the system.
     */
    val modules: ObservableState<Set<Module>>

    /**
     * Returns the first [Module] implementation that implements or extends the given [Class].
     *
     * @param clazz The Class or Interface to match against
     * @param callback The block of code to receive the [Module]
     */
    fun <T> getModuleOfType(clazz: Class<T>, callback: TealiumCallback<T?>)
}

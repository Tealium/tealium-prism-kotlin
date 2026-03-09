package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.settings.barriers.BatchingBarrierSettingsBuilder
import com.tealium.prism.core.api.settings.barriers.ConnectivityBarrierSettingsBuilder
import com.tealium.prism.core.internal.barriers.BatchingBarrier
import com.tealium.prism.core.internal.network.ConnectivityBarrier

/**
 * Utility object for getting built-in [BarrierFactory] objects when configuring the Tealium instance.
 *
 * Some barriers are added to the system by default, but remain accessible here to allow users to
 * override the "scopes" that they apply to.
 *
 * @see Barrier
 * @see BarrierScope
 */
object Barriers {

    /**
     * Returns the [BarrierFactory] for creating the "ConnectivityBarrier". Use this barrier to only
     * dispatch events when connectivity is required.
     *
     * By default, this barrier is active and scoped to Collect module.
     * You can call the following method to programmatically change the scope (or other barrier settings),
     * or you can use local/remote settings configuration instead.
     * ```kotlin
     *  config.addBarrier(Barriers.connectivity { enforcedSettings ->
     *      enforcedSettings.setScopes(emptySet()) // setting empty scopes deactivates the barrier
     *  })
     * ```
     *
     * @param enforcedSettings A function that takes a [ConnectivityBarrierSettingsBuilder] and returns a configured builder.
     *                         Used to provide programmatic settings that will be enforced and remain constant during
     *                         the lifecycle of the [Barrier]. Other settings will still be affected by Local and Remote settings and updates.
     */
    @JvmOverloads
    fun connectivity(enforcedSettings: ((ConnectivityBarrierSettingsBuilder) -> ConnectivityBarrierSettingsBuilder)? = { it }): BarrierFactory {
        val settings = enforcedSettings?.invoke(ConnectivityBarrierSettingsBuilder())?.build()
            ?: DataObject.EMPTY_OBJECT
        return ConnectivityBarrier.Factory(
            enforcedSettings = settings
        )
    }

    /**
     * Returns the [BarrierFactory] for creating the "BatchingBarrier". Use this barrier to only
     * dispatch events when a certain number of queued events has been reached for any of the
     * Dispatcher in scope.
     *
     * When the BatchingBarrier is auto-registered from [BarrierRegistry], it is not scoped to any module
     * and is therefore inactive until scopes are provided via local or remote settings.
     *
     * When you explicitly add this barrier using the helper below, the created [BatchingBarrier]
     * will always have its default scope set to the Collect dispatcher at the factory level:
     * ```kotlin
     *  config.addBarrier(Barriers.batching())
     * ```
     * This default Collect scope can be overridden (or cleared to deactivate the barrier) using
     * enforced settings, local settings, or remote settings configuration.
     *
     * Example of programmatic approach to override the default scope via enforced settings:
     * ```kotlin
     *  config.addBarrier(Barriers.batching { enforcedSettings ->
     *      enforcedSettings.setScopes(setOf(BarrierScope.Dispatcher("MyDispatcher")))
     *  })
     * ```
     *
     * @param enforcedSettings A function that takes a [BatchingBarrierSettingsBuilder] and returns a configured builder.
     *                         Used to provide programmatic settings that will be enforced and remain constant during
     *                         the lifecycle of the [Barrier]. Other settings will still be affected by Local and Remote settings and updates.
     */
    @JvmOverloads
    fun batching(enforcedSettings: ((BatchingBarrierSettingsBuilder) -> BatchingBarrierSettingsBuilder)? = { it }): BarrierFactory {
        val settings = enforcedSettings?.invoke(BatchingBarrierSettingsBuilder())?.build()
            ?: DataObject.EMPTY_OBJECT
        return BatchingBarrier.Factory(
            defaultScopes = setOf(BarrierScope.Dispatcher(Modules.Types.COLLECT)),
            enforcedSettings = settings
        )
    }
}
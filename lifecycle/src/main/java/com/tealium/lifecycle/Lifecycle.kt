package com.tealium.lifecycle

import com.tealium.core.api.Tealium
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.pubsub.Single
import com.tealium.lifecycle.internal.LifecycleModule
import com.tealium.lifecycle.internal.LifecycleWrapper

/**
 * The Lifecycle Module sends events related to application lifecycle -
 * launch, wake, and sleep.
 */
interface Lifecycle {

    /**
     * Sends a launch event and gathers all lifecycle data at the time the event is triggered.
     * Only use if lifecycle auto-tracking is disabled.
     *
     * @param dataObject Optional data to be sent with launch event.
     */
    fun launch(dataObject: DataObject): Single<TealiumResult<Unit>>

    /**
     * Sends a launch event and gathers all lifecycle data at the time the event is triggered.
     * Only use if lifecycle auto-tracking is disabled.
     */
    fun launch(): Single<TealiumResult<Unit>>

    /**
     * Sends a wake event and gathers all lifecycle data at the time the event is triggered.
     * Only use if lifecycle auto-tracking is disabled.
     *
     * @param dataObject Optional data to be sent with wake event.
     */
    fun wake(dataObject: DataObject): Single<TealiumResult<Unit>>

    /**
     * Sends a wake event and gathers all lifecycle data at the time the event is triggered.
     * Only use if lifecycle auto-tracking is disabled.
     */
    fun wake(): Single<TealiumResult<Unit>>

    /**
     * Sends a sleep event and gathers all lifecycle data at the time the event is triggered.
     * Only use if lifecycle auto-tracking is disabled.
     *
     * @param dataObject Optional data to be sent with sleep event.
     */
    fun sleep(dataObject: DataObject): Single<TealiumResult<Unit>>

    /**
     * Sends a sleep event and gathers all lifecycle data at the time the event is triggered.
     * Only use if lifecycle auto-tracking is disabled.
     */
    fun sleep(): Single<TealiumResult<Unit>>

    companion object {

        /**
         * The [Module.id] of the [Lifecycle] module.
         */
        const val ID = "Lifecycle"

        /**
         * Returns a configured [ModuleFactory] for enabling the Lifecycle Module.
         */
        @JvmStatic
        fun configure(): ModuleFactory {
            return LifecycleModule.Factory()
        }

        /**
         * Returns a configured [ModuleFactory] for enabling Lifecycle.
         *
         * The [enforcedSettings] will be set for the lifetime of [Tealium] instance that this [ModuleFactory]
         * is loaded in, and these settings wll override any that come from other local/remote sources.
         *
         * @param enforcedSettings Lifecycle settings that should override any from any other settings source
         */
        @JvmStatic
        fun configure(enforcedSettings: (LifecycleSettingsBuilder) -> LifecycleSettingsBuilder): ModuleFactory {
            val enforcedSettingsBuilder = enforcedSettings(LifecycleSettingsBuilder())
            return LifecycleModule.Factory(enforcedSettingsBuilder)
        }

        /**
         * Returns the Lifecycle instance for a given Tealium instance
         */
        @JvmStatic
        fun getInstance(tealium: Tealium): Lifecycle {
            return LifecycleWrapper(tealium)
        }
    }
}

/**
 * Returns a configured [ModuleFactory] for enabling the Lifecycle Module.
 */
fun com.tealium.core.api.Modules.lifecycle(): ModuleFactory {
    return Lifecycle.configure()
}

/**
 * Returns a configured [ModuleFactory] for enabling Lifecycle.
 *
 * The [enforcedSettings] will be set for the lifetime of [Tealium] instance that this [ModuleFactory]
 * is loaded in, and these settings wll override any that come from other local/remote sources.
 *
 * @param enforcedSettings Lifecycle settings that should override any from any other settings source
 */
fun com.tealium.core.api.Modules.lifecycle(enforcedSettings: (LifecycleSettingsBuilder) -> LifecycleSettingsBuilder): ModuleFactory {
    return Lifecycle.configure(enforcedSettings)
}

/**
 * The [Module.id] of the [Lifecycle] module.
 */
val com.tealium.core.api.Modules.Types.LIFECYCLE
    get() = Lifecycle.ID

/**
 * Returns the Lifecycle instance for a given Tealium instance
 */
val Tealium.lifecycle: Lifecycle
    get() = Lifecycle.getInstance(this)
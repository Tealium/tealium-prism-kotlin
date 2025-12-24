package com.tealium.prism.lifecycle

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.lifecycle.internal.LifecycleModule
import com.tealium.prism.lifecycle.internal.LifecycleWrapper

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
         * Returns a configured [ModuleFactory] for enabling Lifecycle.
         *
         * The [enforcedSettings] will be set for the lifetime of [Tealium] instance that this [ModuleFactory]
         * is loaded in, and these settings wll override any that come from other local/remote sources.
         *
         * @param enforcedSettings
         *  Lifecycle settings that should override any from any other settings source.
         *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
         *  Omitting this parameter will initialize the module with its default settings.
         */
        @JvmStatic
        @JvmOverloads
        fun configure(enforcedSettings: ((LifecycleSettingsBuilder) -> LifecycleSettingsBuilder)? = { it }): ModuleFactory {
            val enforcedSettingsBuilder = enforcedSettings?.invoke(LifecycleSettingsBuilder())
            return LifecycleModule.Factory(enforcedSettingsBuilder?.build())
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
 * Returns the default [ModuleFactory] implementation that will not create any instances
 * unless there are settings provided from Local or Remote sources.
 */
@JvmField
val DEFAULT_FACTORY: ModuleFactory = Lifecycle.configure(null)

/**
 * Returns a configured [ModuleFactory] for enabling the Lifecycle Module.
 */
fun com.tealium.prism.core.api.Modules.lifecycle(): ModuleFactory =
    Lifecycle.configure()

/**
 * Returns a configured [ModuleFactory] for enabling Lifecycle.
 *
 * The [enforcedSettings] will be set for the lifetime of [Tealium] instance that this [ModuleFactory]
 * is loaded in, and these settings wll override any that come from other local/remote sources.
 *
 * @param enforcedSettings
 *  Lifecycle settings that should override any from any other settings source.
 *  Pass `null` to initialize this module only when some Local or Remote settings are provided.
 *  Omitting this parameter will initialize the module with its default settings.
 */
fun com.tealium.prism.core.api.Modules.lifecycle(enforcedSettings: ((LifecycleSettingsBuilder) -> LifecycleSettingsBuilder)? = { it }): ModuleFactory =
    Lifecycle.configure(enforcedSettings)

/**
 * The [Module.id] of the [Lifecycle] module.
 */
val com.tealium.prism.core.api.Modules.Types.LIFECYCLE
    get() = Lifecycle.ID

/**
 * Returns the Lifecycle instance for a given Tealium instance
 */
val Tealium.lifecycle: Lifecycle
    get() = Lifecycle.getInstance(this)
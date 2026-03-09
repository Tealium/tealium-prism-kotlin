package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.TealiumContext

/**
 * The [BarrierFactory] is responsible for creating new [ConfigurableBarrier] instances.
 */
interface BarrierFactory {

    /**
     * The unique identifier of this barrier.
     * This String will be used to match up barriers scoped in the configuration JSON.
     */
    val id: String

    /**
     * An optional set of default [BarrierScope]s to use in the event that these are not configured
     * in any settings sources.
     *
     * In the case that no settings are found, and no default is available, then [BarrierScope.All]
     * will be used. Therefore applying badly configured [Barrier] implementations to all [Dispatcher]s
     */
    fun defaultScopes(): Set<BarrierScope> = setOf(BarrierScope.All)

    /**
     * Creates a [ConfigurableBarrier] instance using the given [context] and [configuration].
     */
    fun create(context: TealiumContext, configuration: DataObject): ConfigurableBarrier

    /**
     * Returns some optional settings for this barrier that override any other Local or Remote settings fields.
     *
     * Only the values at the specific keys returned in this [DataObject] will be enforced and remain constant during the life of this [Barrier].
     * Other values at other keys that are not present in this [DataObject] can be set by Local or Remote settings
     * and be updated by future Remote settings refreshes during the life of this [Barrier].
     *
     * @return A [DataObject] representing the [BarrierSettings], containing some of the settings used by the [Barrier]
     * that will be enforced and remain constant during the life of this [Barrier].
     */
    fun getEnforcedSettings(): DataObject = DataObject.EMPTY_OBJECT
}
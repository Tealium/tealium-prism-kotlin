package com.tealium.core.api.barriers

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.TealiumContext

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
    fun defaultScope(): Set<BarrierScope> = setOf(BarrierScope.All)

    /**
     * Creates a [ConfigurableBarrier] instance using the given [context] and [configuration].
     */
    fun create(context: TealiumContext, configuration: DataObject): ConfigurableBarrier
}
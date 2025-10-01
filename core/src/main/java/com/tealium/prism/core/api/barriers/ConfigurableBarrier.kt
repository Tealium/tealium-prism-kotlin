package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.data.DataObject

/**
 * The [ConfigurableBarrier] is a specialist [Barrier] implementation that supports updated configuration
 * at runtime.
 */
interface ConfigurableBarrier: Barrier {

    /**
     * The unique identifier of this barrier.
     * This String will be used to match up barriers scoped in the configuration JSON.
     */
    val id: String

    /**
     * Method to notify this [Barrier] that updated [configuration] is available that may affect the
     * [Barrier]'s behavior.
     */
    fun updateConfiguration(configuration: DataObject) {}
}
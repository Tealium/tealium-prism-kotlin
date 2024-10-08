package com.tealium.core.internal.persistence.repositories

import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.internal.persistence.getTimestamp

/**
 * Repository class for registering and managing modules.
 *
 */
interface ModulesRepository {

    enum class ExpirationType {
        Session {
            override val expiryTime: Long
                get() = Expiry.SESSION.expiryTime()
        },
        UntilRestart {
            override val expiryTime: Long
                get() = Expiry.UNTIL_RESTART.expiryTime()
        };

        abstract val expiryTime: Long
    }

    /**
     * Returns the current existing of module names mapped to their id
     */
    val modules: Map<String, Long>

    /**
     * Observable to notify of data expiration. The [Pair.first] will contain the relevant module
     * id and the [Pair.second] will contain the expired key-value pairs
     */
    val onDataExpired: Observable<Map<Long, DataObject>>

    /**
     * Registers a new module, returning the id to use for all data storage requests.
     *
     * @param name the unique name of the module
     * @return the existing id of the module if already registered, or the new id generated
     */
    fun registerModule(name: String): Long // Id

    /**
     * Removes all data stored that is currently expired, that is, data that was expected to expire
     * at a future time; or data that matches the provided [expirationType]
     *
     * Removals are notified via [onDataExpired]
     */
    fun deleteExpired(expirationType: ExpirationType, timestamp: Long = getTimestamp())
}


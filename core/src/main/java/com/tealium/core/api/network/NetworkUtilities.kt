package com.tealium.core.api.network

import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.logger.AlternateLogger
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.internal.network.ResourceCacheImpl
import com.tealium.core.internal.network.ResourceRefresherImpl

/**
 * Utility class to group networking utilities onto a single object for a given [com.tealium.core.Tealium] instance
 *
 * @param connectivity The connectivity provider
 * @param networkClient The shared [NetworkClient] for this instance
 * @param networkHelper The shared [NetworkHelper] for this instance
 */
class NetworkUtilities(
    private val connectivity: Connectivity,
    val networkClient: NetworkClient,
    val networkHelper: NetworkHelper,
    private val logger: AlternateLogger
) : Connectivity by connectivity {

    /**
     * Creates a [ResourceRefresher] that will regularly fetch updated values from a remote source
     * as specified in the given [parameters].
     *
     * Use the [parameters] to dictate how often the [ResourceRefresher] will refresh, and where
     * to fetch updates from.
     *
     * @param dataStore The [DataStore] to store the results in
     * @param deserializer The [TealiumDeserializable] implementation for converting values to/from
     * @param parameters The configuration to control the [ResourceRefresher] behavior
     */
    fun <T : TealiumSerializable> resourceRefresher(
        dataStore: DataStore,
        deserializer: TealiumDeserializable<T>,
        parameters: ResourceRefresher.Parameters,
    ): ResourceRefresher<T> {
        return ResourceRefresherImpl(
            networkHelper,
            dataStore,
            deserializer,
            parameters,
            logger
        )
    }

    /**
     * Creates a [ResourceCache]
     */
    fun <T : TealiumSerializable> resourceCache(
        id: String,
        dataStore: DataStore,
        deserializer: TealiumDeserializable<T>,
    ): ResourceCache<T> {
        return ResourceCacheImpl(
            dataStore,
            id,
            deserializer,
        )
    }
}

package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.internal.network.ResourceCacheImpl
import com.tealium.prism.core.internal.network.ResourceRefresherImpl

/**
 * Utility class to group networking utilities onto a single object for a given [com.tealium.prism.core.Tealium] instance
 *
 * @param connectivity The connectivity provider
 * @param networkClient The shared [NetworkClient] for this instance
 * @param networkHelper The shared [NetworkHelper] for this instance
 */
class NetworkUtilities(
    private val connectivity: Connectivity,
    val networkClient: NetworkClient,
    val networkHelper: NetworkHelper,
    private val logger: Logger
) : Connectivity by connectivity {

    /**
     * Creates a [ResourceRefresher] that will regularly fetch updated values from a remote source
     * as specified in the given [parameters].
     *
     * Use the [parameters] to dictate how often the [ResourceRefresher] will refresh, and where
     * to fetch updates from.
     *
     * @param dataStore The [DataStore] to store the results in
     * @param converter The [DataItemConverter] implementation for converting values to/from
     * @param parameters The configuration to control the [ResourceRefresher] behavior
     */
    fun <T : DataItemConvertible> resourceRefresher(
        dataStore: DataStore,
        converter: DataItemConverter<T>,
        parameters: ResourceRefresher.Parameters,
    ): ResourceRefresher<T> {
        return ResourceRefresherImpl(
            networkHelper,
            dataStore,
            converter,
            parameters,
            logger
        )
    }

    /**
     * Creates a [ResourceCache]
     */
    fun <T : DataItemConvertible> resourceCache(
        id: String,
        dataStore: DataStore,
        converter: DataItemConverter<T>,
    ): ResourceCache<T> {
        return ResourceCacheImpl(
            dataStore,
            id,
            converter,
        )
    }
}

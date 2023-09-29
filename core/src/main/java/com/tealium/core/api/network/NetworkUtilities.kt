package com.tealium.core.api.network

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
): Connectivity by connectivity
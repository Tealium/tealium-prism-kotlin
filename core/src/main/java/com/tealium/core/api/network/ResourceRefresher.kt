package com.tealium.core.api.network

import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.misc.TealiumIOException
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.pubsub.Observable
import java.net.URL

interface ResourceRefresher<T : DataItemConvertible> {

    /**
     * An observable stream of valid resources.
     */
    val resource: Observable<T>

    /**
     * An observable stream of exceptions that occur when fetching or reading/writing
     * from storage.
     */
    val errors: Observable<TealiumIOException>

    /**
     * The [ResourceCache] for storing the results and possible etags in.
     */
    val cache: ResourceCache<T>

    /**
     * Returns whether or not this resource should be refreshed according to all relevant timeouts
     * and error cooldowns.
     */
    val shouldRefresh : Boolean

    /**
     * Requests that a new refresh of the resource take place. No validation is done on the
     * object that is retrieved.
     *
     * A refresh only takes place if all relevant timeout conditions are met.
     */
    fun requestRefresh()

    /**
     * Requests that a new refresh of the resource take place. The resulting object is passed to the
     * [isValid] predicate to determine whether or not this should be considered a successful
     * retrieval.
     *
     * A refresh only takes place if all relevant timeout conditions are met.
     *
     * @param isValid Predicate to determine the resource validity. Return true if valid, else false.
     */
    fun requestRefresh(isValid: (T) -> Boolean)

    /**
     * Updates the refresh interval for this resource.
     *
     * @param interval The new refresh interval
     */
    fun setRefreshInterval(interval: TimeFrame)

    /**
     * Parameters explicitly relating to the behavior of the [ResourceRefresher].
     *
     *
     * @param id The identifier of this refresher. This is used as the key for reading/writing the
     * results to/from
     * @param url The remote location to retrieve an updated version from
     * @param refreshInterval How often to fetch a new version of the resource
     * @param errorCooldownBaseInterval The base interval to increment as a backoff when failures
     * occur during refreshes.
     */
    data class Parameters(
        val id: String,
        val url: URL,
        val refreshInterval: TimeFrame,
        val errorCooldownBaseInterval: TimeFrame? = null
    )
}

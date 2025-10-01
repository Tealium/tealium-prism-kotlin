package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.TealiumIOException
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.inSeconds
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.network.CooldownHelper
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.network.NetworkHelper
import com.tealium.prism.core.api.network.ResourceCache
import com.tealium.prism.core.api.network.ResourceRefresher
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.persistence.database.getTimestamp
import com.tealium.prism.core.internal.pubsub.filterNotNull

class ResourceRefresherImpl<T : DataItemConvertible> internal constructor(
    private val networkHelper: NetworkHelper,
    private val converter: DataItemConverter<T>,
    private var params: ResourceRefresher.Parameters,
    override val cache: ResourceCache<T>,
    private val cooldownHelper: CooldownHelper? = CooldownHelper.create(
        params.refreshInterval,
        params.errorCooldownBaseInterval
    ),
    private val timingProvider: () -> Long = ::getTimestamp,
    private val onResourceLoaded: Subject<T> = Observables.publishSubject(),
    private val _onRefreshError: Subject<TealiumIOException> = Observables.publishSubject(),
    private var lastRefresh: Long? = null,
    private val logger: Logger
) : ResourceRefresher<T> {

    constructor(
        context: TealiumContext,
        cache: ResourceCache<T>,
        converter: DataItemConverter<T>,
        params: ResourceRefresher.Parameters,
    ) : this(
        context.network.networkHelper,
        converter,
        params,
        cache,
        logger = context.logger
    )

    /**
     * Convenience constructor that creates the default [ResourceCache] for the
     */
    constructor(
        context: TealiumContext,
        dataStore: DataStore,
        converter: DataItemConverter<T>,
        params: ResourceRefresher.Parameters,
    ) : this(
        context.network.networkHelper,
        converter,
        params,
        ResourceCacheImpl(dataStore, params.id, converter),
        logger = context.logger
    )

    /**
     * Convenience constructor that creates the default [ResourceCache] for the
     */
    constructor(
        networkHelper: NetworkHelper,
        dataStore: DataStore,
        converter: DataItemConverter<T>,
        params: ResourceRefresher.Parameters,
        logger: Logger
    ) : this(
        networkHelper,
        converter,
        params,
        ResourceCacheImpl(dataStore, params.id, converter),
        logger = logger
    )

    private var refreshDisposable: Disposable? = null
    private var isCached: Boolean = cache.resource != null
    private var lastEtag: String? = if (isCached) cache.etag else null

    override val resource: Observable<T>
        get() = Observables.just(readResource())
            .filterNotNull()
            .merge(onResourceLoaded)

    override val errors: Observable<TealiumIOException>
        get() = _onRefreshError.asObservable()


    private fun readResource(): T? = cache.resource.also {
        isCached = it != null
    }

    private val refreshing: Boolean
        get() = refreshDisposable != null

    override val shouldRefresh: Boolean
        get() {
            if (refreshing)
                return false

            val lastRefresh = this.lastRefresh ?: return true

            if (cooldownHelper != null && !isCached)
                return !cooldownHelper.isInCooldown(lastRefresh)

            return lastRefresh + params.refreshInterval.inSeconds() < timingProvider()
        }

    override fun requestRefresh() = requestRefresh { true }

    override fun requestRefresh(isValid: (T) -> Boolean) {
        if (!shouldRefresh) return

        refresh(isValid)
    }

    private fun refresh(isValid: (T) -> Boolean) {
        refreshDisposable =
            networkHelper.getDataItemConvertible(params.url, lastEtag, converter) { result ->
                try {
                    val value = result.getOrThrow()
                    handleNetworkSuccess(value, isValid)
                } catch (e: NetworkException) {
                    handleNetworkFailure(e)
                }
                lastRefresh = timingProvider()
                refreshDisposable = null
            }
    }

    private fun handleNetworkSuccess(
        response: NetworkHelper.HttpValue<T>,
        isValid: (T) -> Boolean
    ) {
        if (isValid(response.value)) {
            logger.debug(
                LogCategory.RESOURCE_REFRESHER,
                "Refreshed resource (%s)", params.id
            )
            saveResource(
                response.value,
                response.httpResponse.headers[HttpRequest.Headers.ETAG]?.firstOrNull()
            )
            onResourceLoaded.onNext(response.value)
            cooldownHelper?.updateStatus(CooldownHelper.CooldownStatus.Success)
        } else {
            // TODO - not an error.... but also not a success. Should Cooldown still be updated?
            logger.debug(
                LogCategory.RESOURCE_REFRESHER,
                "Downloaded resource (%s) but discarded as not valid", params.id
            )
            cooldownHelper?.updateStatus(CooldownHelper.CooldownStatus.Failure)
        }
    }

    private fun handleNetworkFailure(exception: NetworkException) {
        if (exception is NetworkException.Non200Exception && exception.statusCode == 304) {
            logger.trace(
                LogCategory.RESOURCE_REFRESHER,
                "Resource (%s) is not modified", params.id
            )
            cooldownHelper?.updateStatus(CooldownHelper.CooldownStatus.Success)
            return
        }

        logger.error(
            LogCategory.RESOURCE_REFRESHER,
            "Failed to refresh resource (%s).\nError: %s", params.id, exception.message.toString()
        )
        _onRefreshError.onNext(exception)
        cooldownHelper?.updateStatus(CooldownHelper.CooldownStatus.Failure)
    }

    override fun setRefreshInterval(interval: TimeFrame) {
        params = params.copy(refreshInterval = interval)
        cooldownHelper?.maxInterval = interval
    }

    private fun saveResource(resource: T, etag: String?) {
        try {
            cache.saveResource(resource, etag)
            lastEtag = etag
            isCached = true
            logger.trace(
                LogCategory.RESOURCE_REFRESHER,
                "Resource (%s) saved in the cache:\n %s", params.id, resource.asDataItem()
            )
        } catch (e: PersistenceException) {
            logger.error(
                LogCategory.RESOURCE_REFRESHER,
                "Failed to save downloaded resource (%s).\nError: %s",
                params.id,
                e.message.toString()
            )
            _onRefreshError.onNext(e)
        }
    }
}
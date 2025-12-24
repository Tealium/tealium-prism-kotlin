package com.tealium.prism.core.internal

import com.tealium.prism.core.api.InstanceManager
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.misc.Schedulers
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.misc.SchedulersImpl
import com.tealium.prism.core.internal.misc.SingleThreadedScheduler
import com.tealium.prism.core.internal.misc.ThreadPoolScheduler
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.api.pubsub.addTo

class TealiumInstanceManager(
    private val schedulers: Schedulers = SchedulersImpl(
        tealium = SingleThreadedScheduler("tealium"),
        io = ThreadPoolScheduler(0)
    ),
    private val instances: MutableMap<String, TealiumComponents> = mutableMapOf(),
    private val tealiumSupplier: (TealiumConfig, Schedulers) -> TealiumImpl = { conf, sch ->
        TealiumImpl(conf, sch)
    }
) : InstanceManager {

    override fun create(
        config: TealiumConfig,
        onReady: Callback<TealiumResult<Tealium>>?
    ): Tealium {
        val tealiumReady = Observables.replaySubject<TealiumResult<TealiumImpl>>(1)
        val proxy = TealiumProxy(config.key, schedulers.tealium, tealiumReady, ::shutdown)

        getExistingTealiumComponents(config.key) { tealiumComponents ->
            if (tealiumComponents == null) {
                createTealiumComponents(config, proxy, tealiumReady, onReady)
                return@getExistingTealiumComponents
            }

            tealiumComponents.instanceSubject
                .subscribe(tealiumReady)
                .addTo(tealiumComponents.subscriptions)

            tealiumComponents.instance.logger.warn(
                LogCategory.TEALIUM,
                "Duplicate Tealium instance requested for \"${config.key}\". Returning existing one."
            )
            onReady?.onComplete(TealiumResult.success(proxy))
        }

        return proxy
    }

    override fun shutdown(instanceKey: String) {
        schedulers.tealium.execute {
            val tealium = instances.remove(instanceKey)
            if (tealium != null) {
                tealium.instance.shutdown()
                tealium.instanceSubject.onNext(
                    TealiumResult.failure(
                        Tealium.TealiumShutdownException(
                            "Tealium Instance for key ($instanceKey) has been shutdown."
                        )
                    )
                )

                // clean up proxy subscriptions.
                tealium.subscriptions.dispose()
            }
        }
    }

    override fun get(instanceKey: String, callback: Callback<Tealium?>) =
        getExistingTealiumComponents(instanceKey) { components ->
            callback.onComplete(components?.proxy)
        }

    /**
     * Asynchronously fetches an existing [TealiumComponents] object.
     *
     * @param key The key that identifies this [TealiumComponents]
     * @param callback The block of code to receive the result, or null
     */
    private fun getExistingTealiumComponents(
        key: String,
        callback: Callback<TealiumComponents?>
    ) {
        schedulers.tealium.execute {
            callback.onComplete(instances[key])
        }
    }

    /**
     * Creates a new [TealiumImpl] instance, and handles the relevant subscriptions to the provided
     * [proxySubject].
     *
     * @param config The configuration for the new instance
     * @param proxy The TealiumProxy instance that was created to wrap the new instance.
     * @param proxySubject The Subject that the proxy relies on
     * @param onReady The end-user callback to signify that the [Tealium] instance has finished initializing, or has failed.
     */
    private fun createTealiumComponents(
        config: TealiumConfig,
        proxy: TealiumProxy,
        proxySubject: Subject<TealiumResult<TealiumImpl>>,
        onReady: Callback<TealiumResult<Tealium>>?
    ) {
        val instanceSubject = Observables.replaySubject<TealiumResult<TealiumImpl>>(1)
        try {
            val tealiumImpl = tealiumSupplier(
                config,
                schedulers
            )

            val components = TealiumComponents(tealiumImpl, instanceSubject, proxy)
            instances[config.key] = components

            val result = TealiumResult.success(tealiumImpl)

            instanceSubject.onNext(result)
            instanceSubject.subscribe(proxySubject)
                .addTo(components.subscriptions)

            onReady?.onComplete(TealiumResult.success(proxy))
        } catch (ex: Exception) {
            proxySubject.onNext(TealiumResult.failure(ex))
            onReady?.onComplete(TealiumResult.failure(ex))
        }
    }

    /**
     * Helper class to store the actual [TealiumImpl] instance, alongside the first created [TealiumProxy]
     * for retrieval later on if required.
     *
     * The [subscriptions] are only for storing the subscription between the [TealiumProxy] and the
     * main [instance] observable. Disposing this subscription will release all external references to
     * the single [TealiumImpl] object.
     *
     * @param instance The subject to hold the [TealiumImpl] reference
     * @param proxy The initial [TealiumProxy] that was returned when the [create] was called
     * @param subscriptions The [CompositeDisposable] used to maintain the subscriptions between any [TealiumProxy] and the main [instance]
     */
    data class TealiumComponents(
        val instance: TealiumImpl,
        val instanceSubject: ReplaySubject<TealiumResult<TealiumImpl>>,
        val proxy: TealiumProxy,
        val subscriptions: CompositeDisposable = DisposableContainer(),
    )
}
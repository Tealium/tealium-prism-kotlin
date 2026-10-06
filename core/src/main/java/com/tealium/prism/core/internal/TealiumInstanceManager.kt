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
import java.io.Closeable

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

    /**
     * The keys of the instances currently being shut down, each with the blocks deferred while that
     * shutdown is in progress. Only accessed on the tealium scheduler.
     */
    private val shuttingDown: MutableMap<String, MutableList<() -> Unit>> = mutableMapOf()

    override fun create(
        config: TealiumConfig,
        onReady: Callback<TealiumResult<Tealium>>?
    ): Tealium {
        val tealiumReady = Observables.replaySubject<TealiumResult<TealiumImpl>>(1)
        val proxy = TealiumProxy(config.key, schedulers.tealium, tealiumReady) { tealium ->
            shutdownIfCurrent(config.key, tealium)
        }

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

    /**
     * Shuts down the instance that the given [tealium] is bound to.
     *
     * If that instance was already shut down, or a different instance has since been created with
     * the same key, this method does nothing.
     */
    override fun shutdown(tealium: Tealium) {
        if (tealium is TealiumProxy) {
            tealium.shutdown()
        } else {
            shutdown(tealium.key)
        }
    }

    override fun shutdown(instanceKey: String) {
        schedulers.tealium.execute {
            whenNotShuttingDown(instanceKey) {
                val components = instances[instanceKey]
                if (components != null) {
                    tearDown(instanceKey, components)
                }
            }
        }
    }

    /**
     * Shuts down the given [tealium] only if it is still the instance stored for the [instanceKey].
     *
     * This prevents a stale [TealiumProxy] from shutting down a replacement created later with the
     * same key. It doesn't need to wait for a shutdown in progress: until that finishes no instance
     * is stored for the key, and a proxy created in the meantime only receives its [TealiumImpl]
     * once its deferred [create] has run.
     */
    private fun shutdownIfCurrent(instanceKey: String, tealium: TealiumImpl) {
        schedulers.tealium.execute {
            val components = instances[instanceKey]
            if (components != null && components.instance === tealium) {
                tearDown(instanceKey, components)
            }
        }
    }

    /**
     * Removes the given [components] from the stored instances and tears them down. Must be called
     * on the tealium scheduler.
     *
     * Any [create], [get] or [shutdown] for the same key made while the shutdown result is being
     * published (e.g. re-entrantly from a subscriber) is deferred until the teardown has finished,
     * and then run in call order.
     *
     * The instance is shut down even if a subscriber throws while the shutdown result is being
     * published. The subscriber's exception then propagates, with any teardown failure attached
     * to it as suppressed.
     */
    private fun tearDown(instanceKey: String, components: TealiumComponents) {
        instances.remove(instanceKey)
        shuttingDown[instanceKey] = mutableListOf()
        val teardown = Closeable {
            try {
                try {
                    components.instance.shutdown()
                } finally {
                    // clean up proxy subscriptions, even if the instance failed to shut down;
                    // completing the subject already does this, unless the completion was
                    // interrupted by a throwing observer.
                    components.subscriptions.dispose()
                }
            } finally {
                shuttingDown.remove(instanceKey)?.forEach { it() }
            }
        }
        // The teardown runs even if a subscriber throws, although escaped exceptions are
        // expected to crash the app: skipping it would leave the key in [shuttingDown], so every
        // later create/get/shutdown for it would be queued forever, a silent hang rather than a
        // crash. Not every caller lets the exception reach the thread either: a shutdown from
        // inside executeModuleTask is caught by AsyncProxyImpl, which would otherwise leave a
        // live, untracked instance. `use` is only here because it keeps the subscriber's
        // exception as the primary one and attaches any teardown failure as suppressed.
        teardown.use {
            // Emit and complete BEFORE shutting down the instance. Shutting down the
            // instance empties the module manager's module list, which would otherwise
            // reach proxy observers as a transient ModuleNotEnabledException instead of
            // the terminal shutdown error. Completing tears down the bridges to each
            // proxy subject and signals completion to downstream operators.
            components.instanceSubject.onNext(
                TealiumResult.failure(
                    Tealium.TealiumShutdownException(
                        "Tealium Instance for key ($instanceKey) has been shutdown."
                    )
                )
            )
            components.instanceSubject.onComplete()
        }
    }

    override fun get(instanceKey: String, callback: Callback<Tealium?>) =
        getExistingTealiumComponents(instanceKey) { components ->
            callback.onComplete(components?.proxy)
        }

    /**
     * Runs the given [block] immediately, unless an instance with the given [key] is shutting down,
     * in which case it is queued and run once that shutdown has finished. Must be called on the
     * tealium scheduler.
     *
     * The tealium scheduler can't be used to defer the [block], as it runs work inline when already
     * called from the tealium thread.
     */
    private fun whenNotShuttingDown(key: String, block: () -> Unit) {
        val deferred = shuttingDown[key]
        if (deferred != null) {
            deferred.add(block)
        } else {
            block()
        }
    }


    /**
     * Asynchronously fetches an existing [TealiumComponents] object.
     *
     * If an instance with the given [key] is shutting down, the [callback] is deferred until that
     * shutdown has finished.
     *
     * @param key The key that identifies this [TealiumComponents]
     * @param callback The block of code to receive the result, or null
     */
    private fun getExistingTealiumComponents(
        key: String,
        callback: Callback<TealiumComponents?>
    ) {
        schedulers.tealium.execute {
            whenNotShuttingDown(key) {
                callback.onComplete(instances[key])
            }
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
            proxySubject.onComplete()
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

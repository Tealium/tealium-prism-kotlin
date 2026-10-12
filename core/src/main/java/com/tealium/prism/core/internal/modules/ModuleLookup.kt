package com.tealium.prism.core.internal.modules

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.misc.success
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleShutdownException
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.addTo

/**
 * Resolves a [Module] of type [T] on behalf of the [Module] identified by [requesterId], waiting for
 * it when the module system hasn't finished loading yet.
 *
 * Every lookup completes exactly once: with the module, with [ModuleNotEnabledException] when it
 * isn't enabled, or with [ModuleShutdownException] when the requesting module is shut down while the
 * lookup is still pending. Requesting modules should therefore [dispose] this from their
 * [Module.onShutdown].
 *
 * @param moduleManager The [ModuleManager] to resolve the module from
 * @param clazz The [Class] of the [Module] to resolve
 * @param targetModuleType The [Modules.Types] constant of the [Module] to resolve, used in the
 * [ModuleNotEnabledException] message
 * @param requesterId The [Module.id] of the module performing the lookups
 */
class ModuleLookup<T : Module>(
    private val moduleManager: ModuleManager,
    private val clazz: Class<T>,
    private val targetModuleType: String,
    private val requesterId: String
) : Disposable {

    /** Lookups awaiting [ModuleManager.observeModule], failed on shutdown. */
    private val pendingLookups: CompositeDisposable = Disposables.composite()

    override val isDisposed: Boolean
        get() = pendingLookups.isDisposed

    /**
     * Resolves the [Module], providing it to the [callback] as a [TealiumResult].
     */
    fun onModule(callback: Callback<TealiumResult<T>>) {
        // fails the callback if shutdown disposes this before the module resolves
        val pending = Disposables.composite()
        Disposables.subscription {
            // onShutdown() can't tell instance shutdown from settings-disable: claim neither
            callback.failure(ModuleShutdownException(requesterId))
        }.addTo(pending)

        pendingLookups.add(pending)
        // already shut down: the guard fired, don't subscribe too
        if (pending.isDisposed) return

        moduleManager.observeModule(clazz).subscribeOnce { module ->
            pendingLookups.remove(pending)
            if (module == null) {
                callback.failure(ModuleNotEnabledException("Module ($targetModuleType) is not enabled."))
            } else {
                callback.success(module)
            }
        }.addTo(pending)
    }

    override fun dispose() {
        pendingLookups.dispose()
    }
}

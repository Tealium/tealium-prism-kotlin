package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable

/**
 * The [ModuleManager] is responsible for managing [Module] implementations throughout the [Tealium]
 * instance lifecycle.
 *
 * This class provides the means of interacting with other [Module] implementations, either by
 * fetching a specific [Module] implementation or by observing which [Module]s are enabled.
 *
 * Nothing is emitted while the modules are being created or updated, so a subscriber arriving
 * mid-pass waits for that pass to complete instead of being handed the answer of the previous one.
 * Once emitting, `null` / `emptyList()` means the [Module] is not enabled, never that it has not
 * been created yet. The resolved [Module]s are emitted again only when they change by identity or
 * order, so a [Module] reconfigured in place by [Module.updateConfiguration] is not re-emitted. A
 * value that varies over time for the same [Module] is not re-emitted: observe it through the
 * [Module] itself.
 */
interface ModuleManager {

    /**
     * Observes the [Module] matching the given [clazz], regardless of whether it is currently
     * enabled.
     *
     * While the modules are being created or updated, a subscriber receives no value at all rather
     * than a premature `null`. Once this starts emitting, `null` means the [Module] is not enabled.
     *
     * When multiple modules match [clazz] at the same time, the first one, ordered by
     * [com.tealium.prism.core.internal.settings.ModuleSettings.order], is emitted.
     *
     * @param clazz The Class or Interface to match against
     *
     * @return An [Observable] emitting after every modules update in which the matching [Module]
     * changes by identity.
     */
    fun <T : Module> observeModule(clazz: Class<T>): Observable<T?>

    /**
     * Observes every [Module] matching the given [clazz], regardless of whether they are currently
     * enabled.
     *
     * While the modules are being created or updated, a subscriber receives no value at all rather
     * than a premature `emptyList()`. Once this starts emitting, an empty list means no [Module]
     * matching [clazz] is enabled.
     *
     * The emitted modules are ordered by [com.tealium.prism.core.internal.settings.ModuleSettings.order].
     *
     * @param clazz The Class or Interface to match against
     *
     * @return An [Observable] emitting after every modules update in which the matching [Module]s
     * change by identity or order.
     */
    fun <T : Module> observeModules(clazz: Class<T>): Observable<List<T>>

    /**
     * Asynchronously retrieves the first [Module] implementation that implements or extends the
     * given [clazz]. The [callback] is called exactly once, and only once the modules are ready:
     * a `null` result therefore means the [Module] is not enabled, and never that it has not been
     * created yet.
     *
     * After a [Tealium] shutdown the [callback] completes with `null`: no module is ever going to
     * arrive, so waiting for one is the one answer that would never come.
     *
     * @param clazz The Class or Interface to match against
     * @param callback The block of code to receive the [Module]
     *
     * @return A [Disposable] to abandon the lookup while it is still waiting for the modules.
     * Disposing after the [callback] has been called has no effect, and the result can be ignored
     * when the [callback] is always wanted.
     */
    fun <T : Module> getModuleOfType(clazz: Class<T>, callback: Callback<T?>): Disposable
}

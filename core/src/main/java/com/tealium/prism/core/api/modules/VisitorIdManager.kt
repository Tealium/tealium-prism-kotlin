package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.SingleResult
import com.tealium.prism.core.api.pubsub.Subscribable
import com.tealium.prism.core.api.settings.modules.VisitorIdSettingsBuilder

/**
 * The anonymous visitor ID of this device, and the Visitor Switching state behind it.
 *
 * This object is itself subscribable: subscribing emits the current visitor ID first, then again
 * on every change, whether caused by [regenerate], [eraseLocalHistory], or an identity-driven
 * switch when Visitor Switching is configured. Emissions are delivered on the Tealium thread.
 *
 * Visitor Switching is driven entirely by the data layer. An identity is **present** when the
 * visitor identity key (see [VisitorIdSettingsBuilder.setVisitorIdentityKey]) holds a value in the
 * data layer, and **absent** when that key has been removed or has expired. The SDK has no notion
 * of login or logout: a login is the app writing the identity to that key, a logout is the app
 * removing it.
 *
 * Visitor ID handling can be disabled through settings. While visitor ID handling is disabled,
 * subscribers receive a failure carrying [ModuleNotEnabledException] instead of a value, both at
 * subscription time and if it becomes disabled later; successes resume if it is re-enabled. These
 * failures do not complete the subscription. If the [Tealium] instance failed to initialize, or is
 * later shut down, subscribers receive a single failure carrying that exception
 * ([Tealium.TealiumShutdownException] on shutdown) and the subscription completes.
 *
 * Every method returns a failure result while visitor ID handling is disabled, and after the
 * [Tealium] instance failed to initialize or was shut down.
 */
interface VisitorIdManager : Subscribable<TealiumResult<String>> {

    /**
     * The visitor ID at the time of the call.
     *
     * Equivalent to the first emission of a new subscription. To follow subsequent changes,
     * subscribe to this object instead.
     *
     * @return The current visitor ID, or a failure if visitor ID handling is disabled.
     */
    fun current(): SingleResult<String>

    /**
     * Generates a new anonymous visitor ID and returns it.
     *
     * The identity that owns the current visitor ID, if any, takes ownership of the new ID
     * and its stored association is updated, even if that identity has since been removed.
     *
     * If the new visitor ID can't be stored, this fails with a [PersistenceException], but the new
     * visitor ID is still used until the app is relaunched: it is emitted to subscribers and added
     * to events. The stored data is left unchanged, so the previous visitor ID is restored on the
     * next launch.
     *
     * @return The new visitor ID, or a failure if visitor ID handling is disabled.
     */
    fun regenerate(): SingleResult<String>

    /**
     * Erases every identity association Visitor Switching has stored on this device, then
     * generates a new anonymous visitor ID and returns it.
     *
     * Erased data: the hashed identities and the visitor IDs associated with them. No prior
     * association survives the call.
     *
     * If the erasure can't be stored, this fails with a [PersistenceException] and nothing is
     * erased, but the new visitor ID is still used until the app is relaunched: it is emitted to
     * subscribers and added to events. On the next launch, the previous visitor ID and every stored
     * association are restored.
     *
     * This is a device-only operation intended for data-deletion requests. It does not remove the
     * identity from the data layer, and it does not delete or alter server-side visitor profiles;
     * those must be handled separately.
     *
     * @return The new visitor ID, or a failure if visitor ID handling is disabled.
     */
    fun eraseLocalHistory(): SingleResult<String>
}

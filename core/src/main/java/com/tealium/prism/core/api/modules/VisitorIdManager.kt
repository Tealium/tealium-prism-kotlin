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
 * Operations below that depend on an identity being present read the data layer at the time of the call.
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
     * Identity associations stored by Visitor Switching are left untouched. If an identity is present
     * (see [VisitorIdManager]), it takes ownership of the new ID and its stored association is updated.
     * If none is present, the new ID is unowned: the next identity written to the data layer adopts it,
     * and a returning identity restores its own previous visitor ID.
     *
     * Typical uses, both optional:
     * - after removing the identity from the data layer, so that events in the anonymous gap are
     *   not attributed to the previous identity. Remove the key **before** calling this method;
     *   calling it while the identity is still present rebinds that identity to the new ID.
     * - immediately before writing a new identity to the data layer, so that the next identity
     *   does not inherit that gap.
     *
     * The previous visitor ID is not deleted from the device if an identity still owns it; it is
     * restored when that identity returns.
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
     * If an identity is present (see [VisitorIdManager]) it is immediately associated with the new
     * visitor ID, so one new association may exist afterwards. To erase without any association
     * remaining, remove the identity from the data layer first.
     *
     * This is a device-only operation intended for data-deletion requests. It does not remove the
     * identity from the data layer, and it does not delete or alter server-side visitor profiles;
     * those must be handled separately.
     *
     * @return The new visitor ID, or a failure if visitor ID handling is disabled.
     */
    fun eraseLocalHistory(): SingleResult<String>
}

package com.tealium.core.internal

import com.tealium.core.api.DataStore
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Expiry
import com.tealium.core.api.data.TealiumValue

/**
 * [VisitorStorage] provides a set of methods to store, retrieve and associate visitors to known
 * identities.
 *
 * Identities provided to these methods should be consistent and should be hashed to avoid storing
 * personal data unnecessarily.
 */
interface VisitorStorage {

    /**
     * Returns the currently stored visitor id if available.
     *
     * This getter should only return `null` on first creation/launch.
     */
    val visitorId: String?

    /**
     * Returns the currently stored identity if available
     */
    val currentIdentity: String?

    /**
     * Sets the [VisitorStorage.visitorId] to the provided [visitorId]. It does not update the [currentIdentity]
     *
     * Also stores an entry associating [currentIdentity] to [visitorId] such that a previous [visitorId]
     * can be retrieved using [getKnownVisitorId] providing the same identity.
     *
     * @param visitorId The visitor id to store as the current visitor id
     */
    fun changeVisitor(visitorId: String)

    /**
     * Sets the [VisitorStorage.visitorId] to the provided [visitorId] and sets the
     * [VisitorStorage.currentIdentity] to the provided [identity].
     *
     * Also stores an entry associating [identity] to [visitorId] such that a previous [visitorId]
     * can be retrieved using [getKnownVisitorId] providing the same [identity]
     *
     * @param visitorId The visitor id to store as the current visitor id
     * @param identity The identity to store as the current identity, and to associate with the [visitorId]
     */
    fun changeVisitor(visitorId: String, identity: String)

    /**
     * Sets the [VisitorStorage.currentIdentity] to the provided [identity].
     *
     * It does not create any associations between the [currentIdentity] and the [visitorId].
     *
     * @param identity The identity to change to.
     */
    fun changeIdentity(identity: String)

    /**
     * Retrieves a previously seen visitor id that is associated with the given [identity]
     */
    fun getKnownVisitorId(identity: String): String?

    /**
     * Clears all stored VisitorId's and identities, and replaces the [visitorId] with the [newVisitorId]
     *
     * @param newVisitorId the replacement visitor to save after clearing
     */
    fun clear(newVisitorId: String)
}


class VisitorStorageImpl(
    private val storage: DataStore
) : VisitorStorage {

    override val visitorId: String?
        get() = storage.getString(Dispatch.Keys.TEALIUM_VISITOR_ID)

    override val currentIdentity: String?
        get() = storage.getString(KEY_CURRENT_IDENTITY)

    override fun changeVisitor(visitorId: String) {
        storage.edit()
            .setVisitorId(visitorId)
            .associateVisitorWithIdentity(visitorId, currentIdentity)
            .commit()
    }

    override fun changeVisitor(visitorId: String, identity: String) {
        storage.edit()
            .setVisitorId(visitorId)
            .setCurrentIdentity(identity)
            .associateVisitorWithIdentity(visitorId, identity)
            .commit()
    }

    override fun changeIdentity(identity: String) {
        storage.edit()
            .setCurrentIdentity(identity)
            .commit()
    }

    private fun DataStore.Editor.setCurrentIdentity(identity: String): DataStore.Editor = put(
        KEY_CURRENT_IDENTITY,
        TealiumValue.string(identity),
        Expiry.FOREVER
    )

    private fun DataStore.Editor.setVisitorId(visitorId: String): DataStore.Editor = put(
        Dispatch.Keys.TEALIUM_VISITOR_ID,
        TealiumValue.string(visitorId),
        Expiry.FOREVER
    )

    private fun DataStore.Editor.associateVisitorWithIdentity(
        visitorId: String,
        identity: String?
    ): DataStore.Editor =
        identity?.let {
            put(
                identity,
                TealiumValue.string(visitorId),
                Expiry.FOREVER
            )
        } ?: this

    override fun getKnownVisitorId(identity: String): String? =
        storage.getString(identity)

    override fun clear(newVisitorId: String) {
        storage.edit()
            .clear()
            .setVisitorId(newVisitorId)
            .commit()
    }

    private companion object {
        const val KEY_CURRENT_IDENTITY = "current_identity"
    }
}
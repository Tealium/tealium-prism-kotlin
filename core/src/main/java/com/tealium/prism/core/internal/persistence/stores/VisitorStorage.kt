package com.tealium.prism.core.internal.persistence.stores

import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.persistence.PersistenceException

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
    val ownerIdentity: String?

    /**
     * Sets the [VisitorStorage.visitorId] to the provided [visitorId]. It does not update the [ownerIdentity]
     *
     * Also stores an entry associating [ownerIdentity] to [visitorId] such that a previous [visitorId]
     * can be retrieved using [getKnownVisitorId] providing the same identity.
     *
     * @param visitorId The visitor id to store as the current visitor id
     */
    @Throws(PersistenceException::class)
    fun changeVisitor(visitorId: String)

    /**
     * Sets the [VisitorStorage.visitorId] to the provided [visitorId] and sets the
     * [VisitorStorage.ownerIdentity] to the provided [identity].
     *
     * Also stores an entry associating [identity] to [visitorId] such that a previous [visitorId]
     * can be retrieved using [getKnownVisitorId] providing the same [identity]
     *
     * @param visitorId The visitor id to store as the current visitor id
     * @param identity The identity to store as the current identity, and to associate with the [visitorId]
     */
    @Throws(PersistenceException::class)
    fun changeVisitor(visitorId: String, identity: String)

    /**
     * Sets the [VisitorStorage.ownerIdentity] to the provided [identity].
     *
     * It does not create any associations between the [ownerIdentity] and the [visitorId].
     *
     * @param identity The identity to change to.
     */
    @Throws(PersistenceException::class)
    fun changeIdentity(identity: String)

    /**
     * Retrieves a previously seen visitor id that is associated with the given [identity]
     */
    fun getKnownVisitorId(identity: String): String?

    /**
     * Removes the [ownerIdentity] and every stored identity association, and replaces the [visitorId]
     * with the [newVisitorId], in a single commit.
     *
     * Entries not owned by visitor switching are left in place.
     *
     * @param newVisitorId the replacement visitor to save after clearing
     */
    @Throws(PersistenceException::class)
    fun clear(newVisitorId: String)
}


class VisitorStorageImpl(
    private val storage: DataStore
) : VisitorStorage {

    override val visitorId: String?
        get() = storage.getString(KEY_VISITOR_ID)

    override val ownerIdentity: String?
        get() = storage.getString(KEY_OWNER_IDENTITY)

    override fun changeVisitor(visitorId: String) {
        storage.edit()
            .setVisitorId(visitorId)
            .associateVisitorWithIdentity(visitorId, ownerIdentity)
            .commit()
    }

    override fun changeVisitor(visitorId: String, identity: String) {
        storage.edit()
            .setVisitorId(visitorId)
            .setOwnerIdentity(identity)
            .associateVisitorWithIdentity(visitorId, identity)
            .commit()
    }

    override fun changeIdentity(identity: String) {
        storage.edit()
            .setOwnerIdentity(identity)
            .commit()
    }

    private fun DataStore.Editor.setOwnerIdentity(identity: String): DataStore.Editor = put(
        KEY_OWNER_IDENTITY,
        DataItem.string(identity),
        Expiry.FOREVER
    )

    private fun DataStore.Editor.setVisitorId(visitorId: String): DataStore.Editor = put(
        KEY_VISITOR_ID,
        DataItem.string(visitorId),
        Expiry.FOREVER
    )

    private fun DataStore.Editor.associateVisitorWithIdentity(
        visitorId: String,
        identity: String?
    ): DataStore.Editor =
        identity?.let {
            put(
                identityKey(identity),
                DataItem.string(visitorId),
                Expiry.FOREVER
            )
        } ?: this

    override fun getKnownVisitorId(identity: String): String? =
        storage.getString(identityKey(identity))

    override fun clear(newVisitorId: String) {
        val identityKeys = storage.keys()
            .filter { it.startsWith(IDENTITY_KEY_PREFIX) }

        storage.edit()
            .remove(identityKeys + KEY_OWNER_IDENTITY)
            .setVisitorId(newVisitorId)
            .commit()
    }

    private fun identityKey(identity: String): String = IDENTITY_KEY_PREFIX + identity

    companion object {
        internal const val KEY_VISITOR_ID = "visitor_id"
        internal const val KEY_OWNER_IDENTITY = "owner_identity"
        internal const val IDENTITY_KEY_PREFIX = "identity."
    }
}
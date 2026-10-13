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
 *
 * The rows it stores are:
 *  - `visitor_id`: the current visitor id.
 *  - `owner_identity`: the identity that owns the current visitor id; absent while the visitor id
 *  is not owned by any identity.
 *  - `identity.<identity>`: the visitor id that the identity was last associated with.
 */
interface VisitorStorage {

    /**
     * Returns the currently stored visitor id if available.
     *
     * This getter should only return `null` on first creation/launch.
     */
    val visitorId: String?

    /**
     * Returns the identity that owns the current visitor id, if available.
     */
    val ownerIdentity: String?

    /**
     * Sets the [VisitorStorage.visitorId] to the provided [visitorId].
     *
     * With a non null [identity], also sets the [VisitorStorage.ownerIdentity] to [identity] and
     * stores an entry associating [identity] to [visitorId], such that [visitorId] can later be
     * retrieved using [getKnownVisitorId] providing the same [identity].
     *
     * With a null [identity], removes the [ownerIdentity] so the new [visitorId] is unowned; any
     * existing identity association rows are left untouched.
     *
     * All writes are applied as a single commit.
     *
     * @param visitorId The visitor id to store as the current visitor id
     * @param identity The identity to store as the owner identity, and to associate with the
     *  [visitorId], or `null` to leave the new [visitorId] unowned
     */
    @Throws(PersistenceException::class)
    fun changeVisitor(visitorId: String, identity: String?)

    /**
     * Retrieves a previously seen visitor id that is associated with the given [identity]
     */
    fun getKnownVisitorId(identity: String): String?

    /**
     * Erases every stored identity association and the [ownerIdentity], replaces the [visitorId]
     * with the [newVisitorId], then (with a non null [identity]) sets the [ownerIdentity] to
     * [identity] and associates [identity] with the [newVisitorId].
     *
     * Any other entry of the underlying storage is left untouched. Applied as a single commit.
     *
     * @param newVisitorId the replacement visitor to save after erasing
     * @param identity the identity to associate with [newVisitorId], or `null` to leave it unowned
     */
    @Throws(PersistenceException::class)
    fun clear(newVisitorId: String, identity: String?)
}


class VisitorStorageImpl(
    private val storage: DataStore
) : VisitorStorage {

    override val visitorId: String?
        get() = storage.getString(KEY_VISITOR_ID)

    override val ownerIdentity: String?
        get() = storage.getString(KEY_OWNER_IDENTITY)

    override fun changeVisitor(visitorId: String, identity: String?) {
        val editor = storage.edit()
            .setVisitorId(visitorId)

        if (identity == null) {
            editor.remove(KEY_OWNER_IDENTITY)
                .commit()
            return
        }

        editor.setOwnerIdentity(identity)
            .associateVisitorWithIdentity(visitorId, identity)
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

    override fun clear(newVisitorId: String, identity: String?) {
        val survivingIdentityKey = identity?.let(::identityKey)
        val identityKeys = storage.keys()
            .filter { it.startsWith(IDENTITY_KEY_PREFIX) && it != survivingIdentityKey }
        val keysToRemove =
            if (identity == null) identityKeys + KEY_OWNER_IDENTITY else identityKeys

        val editor = storage.edit()
            .remove(keysToRemove)
            .setVisitorId(newVisitorId)

        if (identity == null) {
            editor.commit()
            return
        }

        editor.setOwnerIdentity(identity)
            .associateVisitorWithIdentity(newVisitorId, identity)
            .commit()
    }

    private fun identityKey(identity: String): String = IDENTITY_KEY_PREFIX + identity

    companion object {
        internal const val KEY_VISITOR_ID = "visitor_id"
        internal const val KEY_OWNER_IDENTITY = "owner_identity"
        internal const val IDENTITY_KEY_PREFIX = "identity."
    }
}
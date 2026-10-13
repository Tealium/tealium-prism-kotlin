package com.tealium.prism.core.internal.persistence

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.internal.persistence.stores.VisitorStorage
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.utils.sha256
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.mockkEditor
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisitorIdStorageTests {

    @RelaxedMockK
    private lateinit var visitorStorage: VisitorStorage

    @RelaxedMockK
    private lateinit var visitorIdMigrator: VisitorIdStorageImpl.VisitorIdMigrator

    private val currentVisitorId = "visitor123"
    private val ownerIdentity = "identity123"
    private val hashedOwnerIdentity = ownerIdentity.sha256()
    private val migratingVisitorId = "migrating_id"
    private val existingVisitorId = "existing_visitor_id"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        // Default all storage to empty
        every { visitorStorage.visitorId } returns null
        every { visitorStorage.ownerIdentity } returns null
        every { visitorIdMigrator.visitorId } returns null
    }

    @Test
    fun init_MigratesKnownVisitorId_When_NoVisitorIdSaved_And_DeletesMigratedData() {
        every { visitorIdMigrator.visitorId } returns migratingVisitorId

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)

        assertEquals(migratingVisitorId, visitorIdStorage.visitorId.value)
        verify {
            visitorStorage.changeVisitor(migratingVisitorId, null)
            visitorIdMigrator.delete()
        }
    }

    @Test
    fun init_MigratesKnownVisitorId_FromExistingId_When_NoVisitorIdSaved() {
        val visitorIdStorage = createDefaultVisitorIdStorage(
            visitorStorage = visitorStorage,
        )

        assertEquals(existingVisitorId, visitorIdStorage.visitorId.value)
        verify {
            visitorStorage.changeVisitor(existingVisitorId, null)
        }
    }

    @Test
    fun init_DoesNot_MigrateKnownVisitorId_FromExistingId_When_NoVisitorIdSaved_And_ExistingVisitorId_IsBlank() {
        val visitorIdStorage = createCustomVisitorIdStorage(
            existingVisitorId = " ",
            visitorStorage = visitorStorage
        )

        assertTrue(visitorIdStorage.visitorId.value.isNotBlank())
        verify {
            visitorStorage.changeVisitor(match { it.isNotBlank() }, null)
        }
    }

    @Test
    fun init_DoesNot_MigrateKnownVisitorId_FromExistingId_When_NoVisitorIdSaved_And_ExistingVisitorId_IsEmpty() {
        val visitorIdStorage = createCustomVisitorIdStorage(
            existingVisitorId = "",
            visitorStorage = visitorStorage
        )

        assertTrue(visitorIdStorage.visitorId.value.isNotEmpty())
        verify {
            visitorStorage.changeVisitor(match { it.isNotEmpty() }, null)
        }
    }

    @Test
    fun init_GeneratesNewId_When_NoVisitorIds() {
        val visitorIdStorage =
            createCustomVisitorIdStorage(visitorStorage = visitorStorage, existingVisitorId = null)

        assertTrue(visitorIdStorage.visitorId.value.isNotBlank())
        assertNotEquals(currentVisitorId, visitorIdStorage.visitorId.value)
        assertNotEquals(migratingVisitorId, visitorIdStorage.visitorId.value)
        assertNotEquals(existingVisitorId, visitorIdStorage.visitorId.value)
    }

    @Test
    fun identify_DoesNot_UpdateAnything_When_BlankOrEmptyIdentity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        visitorIdStorage.identify("")
        visitorIdStorage.identify("   ")

        verify(exactly = 0) {
            visitorStorage.getKnownVisitorId(any())
            visitorStorage.changeVisitor(any(), any())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_DoesNot_UpdateAnything_When_Identity_IsTheSame() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        visitorIdStorage.identify(ownerIdentity)

        verify(exactly = 0) {
            visitorStorage.getKnownVisitorId(any())
            visitorStorage.changeVisitor(any(), any())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_Restores_Known_Visitor_When_Current_Visitor_Id_Is_Unowned() {
        // after a regenerate with no identity present, the current id is unowned; a returning
        // identity restores its own visitor id instead of adopting the unowned one
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns null
        every { visitorStorage.getKnownVisitorId(hashedOwnerIdentity) } returns "knownVisitor"
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        visitorIdStorage.identify(ownerIdentity)

        verify {
            visitorStorage.changeVisitor("knownVisitor", hashedOwnerIdentity)
            onNext("knownVisitor")
        }
        verify(inverse = true) {
            visitorStorage.changeVisitor(currentVisitorId, hashedOwnerIdentity)
        }
    }

    @Test
    fun identify_Changes_To_Known_Visitor_When_Identity_IsNot_TheSame() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns "knownVisitor"
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        visitorIdStorage.identify("newIdentity")

        verify {
            visitorStorage.getKnownVisitorId("newIdentity".sha256())
            visitorStorage.changeVisitor("knownVisitor", "newIdentity".sha256())
            onNext("knownVisitor")
        }
    }

    @Test
    fun identify_Creates_New_VisitorId_WhenIdentityChanged_And_IsNot_Known_Visitor() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns null
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        visitorIdStorage.identify("newIdentity")

        verify {
            visitorStorage.changeVisitor(neq(currentVisitorId), "newIdentity".sha256())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_Associates_Current_VisitorId_With_NewIdentity_WhenIdentityChanged_But_No_Previous_Identity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns null
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns null
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        visitorIdStorage.identify("newIdentity")

        verify {
            visitorStorage.changeVisitor(currentVisitorId, "newIdentity".sha256())
        }
        verify(exactly = 1) {
            onNext(currentVisitorId)
        }
        confirmVerified(onNext)
    }

    @Test
    fun identify_Stores_Known_Identity_As_Owner_When_It_Already_Uses_Current_Visitor_Id() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns ownerIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns currentVisitorId
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        visitorIdStorage.identify("newIdentity")

        verify {
            visitorStorage.changeVisitor(currentVisitorId, "newIdentity".sha256())
        }
        // once on subscribe; storing the switch to the current id is not a change
        verify(exactly = 1) {
            onNext(currentVisitorId)
        }
        confirmVerified(onNext)
    }

    @Test
    fun identify_Completes_Switch_When_Same_Identity_Rewritten_After_Failed_Store() {
        // identity A is known to use v1 while B owns the stored id v0; the first switch to v1 is
        // published but not stored, so the repeated write of A must store it rather than only
        // rewrite the owner against the stale v0
        every { visitorStorage.visitorId } returns "v0"
        every { visitorStorage.ownerIdentity } returns "B".sha256()
        every { visitorStorage.getKnownVisitorId("A".sha256()) } returns "v1"
        every {
            visitorStorage.changeVisitor("v1", "A".sha256())
        } throws PersistenceException("TestError", mockk()) andThenAnswer { }
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        visitorIdStorage.identify("A")
        assertEquals("v1", visitorIdStorage.visitorId.value)

        visitorIdStorage.identify("A")

        verify(exactly = 2) {
            visitorStorage.changeVisitor("v1", "A".sha256())
        }
        verify(exactly = 1) {
            onNext("v1")
        }
    }

    @Test
    fun regenerate_Replaces_Current_Visitor_Id_And_Associates_It_With_Passed_Identity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        val newVisitorId = visitorIdStorage.regenerate(hashedOwnerIdentity)

        assertNotEquals(currentVisitorId, newVisitorId)
        verify {
            visitorStorage.changeVisitor(newVisitorId, hashedOwnerIdentity)
            onNext(newVisitorId)
        }
    }

    @Test
    fun regenerate_Leaves_New_Visitor_Id_Unowned_When_No_Identity_Passed() {
        // the stored owner is not used: only the identity present at the time of the call counts
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        val newVisitorId = visitorIdStorage.regenerate(null)

        assertNotEquals(currentVisitorId, newVisitorId)
        verify {
            visitorStorage.changeVisitor(newVisitorId, null)
            onNext(newVisitorId)
        }
        verify(inverse = true) {
            visitorStorage.changeVisitor(any(), hashedOwnerIdentity)
        }
    }

    @Test(expected = PersistenceException::class)
    fun regenerate_Throws_When_DataStore_Throws() {
        every { visitorStorage.changeVisitor(any(), any()) } throws PersistenceException("TestError", mockk())

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)

        visitorIdStorage.regenerate(null)
    }

    @Test
    fun regenerate_Publishes_New_VisitorId_When_DataStore_Throws() {
        val dataStore = createDataStoreHolding(currentVisitorId)
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(
            visitorStorage = VisitorStorageImpl(dataStore)
        )
        visitorIdStorage.visitorId.subscribe(onNext)
        every { dataStore.edit() } throws PersistenceException("TestError", mockk())

        assertThrows(PersistenceException::class.java) { visitorIdStorage.regenerate(null) }

        assertNotEquals(currentVisitorId, visitorIdStorage.visitorId.value)
        verify {
            onNext(visitorIdStorage.visitorId.value)
        }
        // nothing stored, so the previous id is what the next launch restores
        assertEquals(currentVisitorId, dataStore.getString(VisitorStorageImpl.KEY_VISITOR_ID))
    }

    @Test
    fun eraseLocalHistory_Replaces_Current_Visitor_Id_And_Clears_Stored_Identities() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns ownerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        val visitorId = visitorIdStorage.eraseLocalHistory(null)

        verify {
            visitorStorage.clear(visitorId, null)
            onNext(visitorId)
        }
    }

    @Test
    fun eraseLocalHistory_Reassociates_Passed_Identity_With_New_Visitor_Id() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)

        val visitorId = visitorIdStorage.eraseLocalHistory(hashedOwnerIdentity)

        verify {
            visitorStorage.clear(visitorId, hashedOwnerIdentity)
            onNext(visitorId)
        }
    }

    @Test(expected = PersistenceException::class)
    fun eraseLocalHistory_Throws_When_DataStore_Throws() {
        every { visitorStorage.clear(any(), any()) } throws PersistenceException("TestError", mockk())

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)

        visitorIdStorage.eraseLocalHistory(null)
    }

    @Test
    fun eraseLocalHistory_Publishes_New_VisitorId_When_DataStore_Throws() {
        val dataStore = createDataStoreHolding(currentVisitorId, hashedOwnerIdentity)
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(
            visitorStorage = VisitorStorageImpl(dataStore)
        )
        visitorIdStorage.visitorId.subscribe(onNext)
        every { dataStore.edit() } throws PersistenceException("TestError", mockk())

        assertThrows(PersistenceException::class.java) { visitorIdStorage.eraseLocalHistory(null) }

        assertNotEquals(currentVisitorId, visitorIdStorage.visitorId.value)
        verify {
            onNext(visitorIdStorage.visitorId.value)
        }
        // nothing erased, so the previous id and its associations are what the next launch restores
        assertEquals(currentVisitorId, dataStore.getString(VisitorStorageImpl.KEY_VISITOR_ID))
        assertEquals(
            hashedOwnerIdentity,
            dataStore.getString(VisitorStorageImpl.KEY_OWNER_IDENTITY)
        )
        assertEquals(
            currentVisitorId,
            dataStore.getString(VisitorStorageImpl.IDENTITY_KEY_PREFIX + hashedOwnerIdentity)
        )
    }

    @Test
    fun identify_Publishes_New_VisitorId_And_DoesNot_Throw_When_DataStore_Throws() {
        // another identity owns the current id and the new one is unknown, so a new id is minted
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns null
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.visitorId.subscribe(onNext)
        every {
            visitorStorage.changeVisitor(any(), any())
        } throws PersistenceException("TestError", mockk())

        visitorIdStorage.identify("newIdentity")

        assertNotEquals(currentVisitorId, visitorIdStorage.visitorId.value)
        verify {
            onNext(visitorIdStorage.visitorId.value)
        }
    }

    @Test
    fun init_Uses_New_VisitorId_Without_Persisting_When_DataStore_Throws() {
        val dataStore = createDataStoreHolding(storedVisitorId = null)
        every { dataStore.edit() } throws PersistenceException("TestError", mockk())

        val visitorIdStorage = createCustomVisitorIdStorage(
            existingVisitorId = null,
            visitorStorage = VisitorStorageImpl(dataStore)
        )

        assertTrue(visitorIdStorage.visitorId.value.isNotBlank())
        assertEquals(null, dataStore.getString(VisitorStorageImpl.KEY_VISITOR_ID))
    }

    @Test
    fun identify_Hashes_Identity_As_Lowercase_Sha256_Hex_When_New_Identity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.getKnownVisitorId(any()) } returns null

        val visitorIdStorage = createDefaultVisitorIdStorage(visitorStorage = visitorStorage)
        visitorIdStorage.identify("newIdentity")

        verify {
            visitorStorage.getKnownVisitorId(NEW_IDENTITY_SHA256)
            visitorStorage.changeVisitor(any(), NEW_IDENTITY_SHA256)
        }
    }

    @Test
    fun identify_Stores_Identity_Key_With_Lowercase_Sha256_When_New_Identity() {
        val editor = mockkEditor()
        val dataStore = mockk<DataStore>()
        every { dataStore.getString(any()) } returns null
        every { dataStore.edit() } returns editor

        val visitorIdStorage = createDefaultVisitorIdStorage(
            visitorStorage = VisitorStorageImpl(dataStore)
        )
        visitorIdStorage.identify("newIdentity")

        verify {
            editor.put(
                VisitorStorageImpl.IDENTITY_KEY_PREFIX + NEW_IDENTITY_SHA256,
                DataItem.string(visitorIdStorage.visitorId.value),
                Expiry.FOREVER
            )
        }
    }

    private fun createDefaultVisitorIdStorage(
        visitorIdMigrator: VisitorIdStorageImpl.VisitorIdMigrator? = null,
        visitorStorage: VisitorStorage,
    ): VisitorIdStorage {
        return createCustomVisitorIdStorage(
            existingVisitorId,
            visitorIdMigrator,
            visitorStorage,
        )
    }

    private fun createCustomVisitorIdStorage(
        existingVisitorId: String? = null,
        visitorIdMigrator: VisitorIdStorageImpl.VisitorIdMigrator? = null,
        visitorStorage: VisitorStorage,
    ): VisitorIdStorage {
        return VisitorIdStorageImpl(
            visitorStorage = visitorStorage,
            migrator = visitorIdMigrator ?: this.visitorIdMigrator,
            existingVisitorId = existingVisitorId,
            logger = SystemLogger
        )
    }

    /**
     * A [DataStore] mock that reads back [storedVisitorId] and, when given, [storedOwnerIdentity]
     * with its association, so a [VisitorStorageImpl] over it behaves like one restored from disk.
     * Writes are left unstubbed for the test to control.
     */
    private fun createDataStoreHolding(
        storedVisitorId: String?,
        storedOwnerIdentity: String? = null
    ): DataStore {
        val stored = buildMap<String, String?> {
            storedVisitorId?.let { put(VisitorStorageImpl.KEY_VISITOR_ID, it) }
            storedOwnerIdentity?.let {
                put(VisitorStorageImpl.KEY_OWNER_IDENTITY, it)
                put(VisitorStorageImpl.IDENTITY_KEY_PREFIX + it, storedVisitorId)
            }
        }
        val dataStore = mockk<DataStore>()
        every { dataStore.getString(any()) } answers { stored[firstArg()] }
        every { dataStore.keys() } returns stored.keys.toList()
        return dataStore
    }

    private companion object {
        /** Lowercase hex SHA-256 of "newIdentity", matching the Swift SDK output. */
        const val NEW_IDENTITY_SHA256 =
            "2d66ba8ba26bf2ee9c7b40d5225e73c1db3a56f158cc9f669fbe340590af62db"
    }
}

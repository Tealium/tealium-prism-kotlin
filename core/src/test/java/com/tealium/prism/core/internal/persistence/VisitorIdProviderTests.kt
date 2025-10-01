package com.tealium.prism.core.internal.persistence

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.persistence.stores.VisitorStorage
import com.tealium.prism.core.internal.utils.sha256
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisitorIdProviderTests {

    @RelaxedMockK
    private lateinit var visitorStorage: VisitorStorage

    @RelaxedMockK
    private lateinit var visitorIdMigrator: VisitorIdProviderImpl.VisitorIdMigrator

    private val onDataLayerUpdated: Subject<DataObject> = spyk(Observables.publishSubject())
    private val currentVisitorId = "visitor123"
    private val currentIdentity = "identity123"
    private val hashedCurrentIdentity = currentIdentity.sha256()
    private val migratingVisitorId = "migrating_id"
    private val existingVisitorId = "existing_visitor_id"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        // Default all storage to empty
        every { visitorStorage.visitorId } returns null
        every { visitorStorage.currentIdentity } returns null
        every { visitorIdMigrator.visitorId } returns null
    }

    @Test
    fun init_MigratesKnownVisitorId_When_NoVisitorIdSaved_And_DeletesMigratedData() {
        every { visitorIdMigrator.visitorId } returns migratingVisitorId

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)

        assertEquals(migratingVisitorId, visitorIdProvider.visitorId.value)
        verify {
            visitorStorage.changeVisitor(migratingVisitorId)
            visitorIdMigrator.delete()
        }
    }

    @Test
    fun init_MigratesKnownVisitorId_FromExistingId_When_NoVisitorIdSaved() {
        val visitorIdProvider = createDefaultVisitorIdProvider(
            visitorStorage = visitorStorage,
        )

        assertEquals(existingVisitorId, visitorIdProvider.visitorId.value)
        verify {
            visitorStorage.changeVisitor(existingVisitorId)
        }
    }

    @Test
    fun init_GeneratesNewId_When_NoVisitorIds() {
        val visitorIdProvider =
            createCustomVisitorIdProvider(visitorStorage = visitorStorage, existingVisitorId = null)

        assertTrue(visitorIdProvider.visitorId.value.isNotBlank())
        assertNotEquals(currentVisitorId, visitorIdProvider.visitorId.value)
        assertNotEquals(migratingVisitorId, visitorIdProvider.visitorId.value)
        assertNotEquals(existingVisitorId, visitorIdProvider.visitorId.value)
    }

    @Test
    fun onDataLayerUpdated_DoesNot_UpdateAnything_When_IncorrectDataKey() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns hashedCurrentIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        onDataLayerUpdated.onNext(DataObject.create {
            put("incorrect_key", "newIdentity")
        })

        verify(inverse = true) {
            visitorStorage.getKnownVisitorId(any())
            visitorStorage.changeVisitor(any(), any())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_DoesNot_UpdateAnything_When_BlankOrEmptyIdentity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns hashedCurrentIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        visitorIdProvider.identify("")
        visitorIdProvider.identify("   ")

        verify(inverse = true) {
            visitorStorage.getKnownVisitorId(any())
            visitorStorage.changeVisitor(any(), any())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_DoesNot_UpdateAnything_When_Identity_IsTheSame() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns hashedCurrentIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        visitorIdProvider.identify(currentIdentity)

        verify(inverse = true) {
            visitorStorage.getKnownVisitorId(any())
            visitorStorage.changeVisitor(any(), any())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_Changes_To_Known_Visitor_When_Identity_IsNot_TheSame() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns hashedCurrentIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns "knownVisitor"
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        visitorIdProvider.identify("newIdentity")

        verify {
            visitorStorage.getKnownVisitorId("newIdentity".sha256())
            visitorStorage.changeVisitor("knownVisitor", "newIdentity".sha256())
            onNext("knownVisitor")
        }
    }

    @Test
    fun identify_Creates_New_VisitorId_WhenIdentityChanged_And_IsNot_Known_Visitor() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns hashedCurrentIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns null
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        visitorIdProvider.identify("newIdentity")

        verify {
            visitorStorage.changeVisitor(neq(currentVisitorId), "newIdentity".sha256())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_DoesNot_Update_Visitor_Or_Identity_WhenIdentityChanged_But_Is_Same_Known_Visitor() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns hashedCurrentIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns currentVisitorId
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        visitorIdProvider.identify("newIdentity")

        verify(inverse = true) {
            visitorStorage.changeVisitor(currentVisitorId, "newIdentity".sha256())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_Associates_Current_VisitorId_With_NewIdentity_WhenIdentityChanged_But_No_Previous_Identity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns null
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns null
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        visitorIdProvider.identify("newIdentity")

        verify {
            visitorStorage.changeVisitor(currentVisitorId, "newIdentity".sha256())
            onNext(currentVisitorId)
        }
        confirmVerified(onNext)
    }

    @Test
    fun identify_ChangesIdentity_WhenIdentityChanged_But_SameVisitorId() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns currentIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns currentVisitorId
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        visitorIdProvider.identify("newIdentity")

        verify {
            visitorStorage.changeIdentity("newIdentity".sha256())
            onNext(currentVisitorId)
        }
        confirmVerified(onNext)
    }

    @Test
    fun resetVisitorId_ResetsCurrentVisitorId_And_AssociatesTo_ExistingIdentity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns currentIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        visitorIdProvider.resetVisitorId()

        verify {
            visitorStorage.changeVisitor(neq(currentVisitorId))
            onNext(neq(currentVisitorId))
        }
    }

    @Test(expected = PersistenceException::class)
    fun resetVisitorId_Throws_When_DataStore_Throws() {
        every { visitorStorage.changeVisitor(any()) } throws PersistenceException("TestError", mockk())

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)

        visitorIdProvider.resetVisitorId()
    }

    fun resetVisitorId_Still_Emits_New_VisitorId_When_DataStore_Throws() {
        every { visitorStorage.changeVisitor(any()) } throws PersistenceException("TestError", mockk())
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        var exception: Exception? = null
        val visitorId = try {
            visitorIdProvider.resetVisitorId()
        } catch (e: Exception) {
            exception = e
            null
        }

        assertTrue(exception is PersistenceException)
        verify {
            visitorStorage.changeVisitor(visitorId!!)
            onNext(visitorId)
        }
    }

    @Test
    fun clearStoredVisitorIds_ResetsCurrentVisitorId_And_ClearsStoredIdentities() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.currentIdentity } returns currentIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        val visitorId = visitorIdProvider.clearStoredVisitorIds()

        verify {
            visitorStorage.clear(visitorId)
            onNext(visitorId)
        }
    }

    @Test(expected = PersistenceException::class)
    fun clearStoredVisitorIds_Throws_When_DataStore_Throws() {
        every { visitorStorage.clear(any()) } throws PersistenceException("TestError", mockk())

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)

        visitorIdProvider.clearStoredVisitorIds()
    }

    fun clearStoredVisitorIds_Still_Emits_New_VisitorId_When_DataStore_Throws() {
        every { visitorStorage.clear(any()) } throws PersistenceException("TestError", mockk())
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdProvider = createDefaultVisitorIdProvider(visitorStorage = visitorStorage)
        visitorIdProvider.visitorId.subscribe(onNext)

        var exception: Exception? = null
        val visitorId = try {
            visitorIdProvider.clearStoredVisitorIds()
        } catch (e: Exception) {
            exception = e
            null
        }

        assertTrue(exception is PersistenceException)
        verify {
            visitorStorage.clear(visitorId!!)
            onNext(visitorId)
        }
    }

    private fun createDefaultVisitorIdProvider(
        visitorIdMigrator: VisitorIdProviderImpl.VisitorIdMigrator? = null,
        visitorStorage: VisitorStorage,
    ): VisitorIdProvider {
        return createCustomVisitorIdProvider(
            existingVisitorId,
            visitorIdMigrator,
            visitorStorage,
        )
    }

    private fun createCustomVisitorIdProvider(
        existingVisitorId: String? = null,
        visitorIdMigrator: VisitorIdProviderImpl.VisitorIdMigrator? = null,
        visitorStorage: VisitorStorage,
    ): VisitorIdProvider {
        return VisitorIdProviderImpl(
            visitorStorage = visitorStorage,
            migrator = visitorIdMigrator ?: this.visitorIdMigrator,
            existingVisitorId = existingVisitorId,
            logger = SystemLogger
        )
    }
}
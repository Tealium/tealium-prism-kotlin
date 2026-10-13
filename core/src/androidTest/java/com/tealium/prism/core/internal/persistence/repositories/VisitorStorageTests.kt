package com.tealium.prism.core.internal.persistence.repositories

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.internal.persistence.database.DatabaseProvider
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.stores.VisitorStorage
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.utils.sha256
import com.tealium.tests.common.getDefaultConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VisitorStorageTests {

    private val context: Application = ApplicationProvider.getApplicationContext() as Application
    private val config = getDefaultConfig(context)

    private lateinit var dataStore: DataStore
    private lateinit var visitorStorage: VisitorStorage
    private lateinit var dbProvider: DatabaseProvider

    @Before
    fun setUp() {
        dbProvider = InMemoryDatabaseProvider(config)
        val modulesRepository = SQLModulesRepository(dbProvider)
        val moduleStoreProvider = ModuleStoreProviderImpl(dbProvider, modulesRepository)
        dataStore = moduleStoreProvider.getSharedDataStore()

        visitorStorage = VisitorStorageImpl(dataStore)
    }

    @After
    fun tearDown() {
        dbProvider.database.close()
    }

    @Test
    fun visitorId_Returns_Null_When_No_Entry() {
        assertNull(visitorStorage.visitorId)
    }

    @Test
    fun ownerIdentity_Returns_Null_When_No_Entry() {
        assertNull(visitorStorage.ownerIdentity)
    }

    @Test
    fun visitorId_Returns_NewVisitorId_After_ChangingVisitor() {
        visitorStorage.changeVisitor("visitor", null)

        assertEquals("visitor", visitorStorage.visitorId)
    }

    @Test
    fun ownerIdentity_Returns_NewIdentity_After_ChangingVisitor() {
        visitorStorage.changeVisitor("visitor", "identity")

        assertEquals("identity", visitorStorage.ownerIdentity)
    }

    @Test
    fun getKnownVisitorId_Returns_Known_VisitorId() {
        visitorStorage.changeVisitor("visitor", "identity")

        assertEquals("visitor", visitorStorage.getKnownVisitorId("identity"))
    }

    @Test
    fun getKnownVisitorId_Returns_Previous_VisitorIds() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeVisitor("new_visitor", "new_identity")

        assertEquals("visitor", visitorStorage.getKnownVisitorId("identity"))
        assertEquals("new_visitor", visitorStorage.getKnownVisitorId("new_identity"))
    }

    @Test
    fun changeVisitor_Removes_Owner_Identity_And_Leaves_Identity_Rows_When_Identity_Null() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeVisitor("new_visitor", null)

        assertEquals("new_visitor", visitorStorage.visitorId)
        assertNull(visitorStorage.ownerIdentity)
        assertEquals("visitor", visitorStorage.getKnownVisitorId("identity"))
    }

    @Test
    fun changeVisitor_DoesNot_Associate_New_VisitorId_When_Identity_Null() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeVisitor("new_visitor", null)

        assertEquals(listOf(identityKey("identity")), identityRows())
    }

    @Test
    fun changeVisitor_Replaces_Both_VisitorId_And_Identity() {
        visitorStorage.changeVisitor("visitor", "identity")
        assertEquals("visitor", visitorStorage.visitorId)
        assertEquals("identity", visitorStorage.ownerIdentity)

        visitorStorage.changeVisitor("new_visitor", "new_identity")
        assertEquals("new_visitor", visitorStorage.visitorId)
        assertEquals("new_identity", visitorStorage.ownerIdentity)
    }

    @Test
    fun clear_Removes_All_VisitorData_And_Sets_New_VisitorId() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeVisitor("new_visitor", "new_identity")

        visitorStorage.clear("reset_visitor", null)

        assertEquals("reset_visitor", visitorStorage.visitorId)
        assertNull(visitorStorage.ownerIdentity)
        assertNull(visitorStorage.getKnownVisitorId("identity"))
        assertNull(visitorStorage.getKnownVisitorId("new_identity"))
    }

    @Test
    fun clear_Keeps_Surviving_Association_And_Sets_Owner_When_Identity_Given() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeVisitor("new_visitor", "new_identity")

        visitorStorage.clear("reset_visitor", "identity")

        assertEquals("reset_visitor", visitorStorage.visitorId)
        assertEquals("identity", visitorStorage.ownerIdentity)
        assertEquals("reset_visitor", visitorStorage.getKnownVisitorId("identity"))
        assertNull(visitorStorage.getKnownVisitorId("new_identity"))
        assertEquals(listOf(identityKey("identity")), identityRows())
    }

    @Test
    fun getKnownVisitorId_Returns_VisitorId_Stored_Under_Prefixed_Hashed_Identity_Key() {
        val hashedIdentity = "identity".sha256()

        visitorStorage.changeVisitor("visitor", hashedIdentity)

        assertEquals(
            "visitor",
            dataStore.getString(VisitorStorageImpl.IDENTITY_KEY_PREFIX + hashedIdentity)
        )
        assertNull(dataStore.getString(hashedIdentity))
        assertEquals("visitor", visitorStorage.getKnownVisitorId(hashedIdentity))
    }

    @Test
    fun visitorId_Is_Stored_Under_Visitor_Id_Key() {
        visitorStorage.changeVisitor("visitor", null)

        assertEquals("visitor", dataStore.getString(VisitorStorageImpl.KEY_VISITOR_ID))
    }

    @Test
    fun ownerIdentity_Is_Stored_Under_Owner_Identity_Key() {
        visitorStorage.changeVisitor("visitor", "identity")

        assertEquals("identity", dataStore.getString(VisitorStorageImpl.KEY_OWNER_IDENTITY))
    }

    @Test
    fun clear_Leaves_Unrelated_Entries_Intact() {
        dataStore.edit()
            .put("unrelated", DataItem.string("value"), Expiry.FOREVER)
            .commit()
        visitorStorage.changeVisitor("visitor", "identity".sha256())

        visitorStorage.clear("reset_visitor", null)

        assertEquals("value", dataStore.getString("unrelated"))
        assertEquals("reset_visitor", visitorStorage.visitorId)
        assertNull(visitorStorage.ownerIdentity)
        assertNull(visitorStorage.getKnownVisitorId("identity".sha256()))
    }

    @Test
    fun getKnownVisitorId_Round_Trips_Dotted_Identity_Key_Through_Reopened_Store() {
        val dottedIdentity = "first.last@example.com"
        visitorStorage.changeVisitor("visitor", dottedIdentity)

        // a new store over the same database, so values are read back from their stored rows
        val reopenedStore = ModuleStoreProviderImpl(dbProvider, SQLModulesRepository(dbProvider))
            .getSharedDataStore()
        val reopenedStorage = VisitorStorageImpl(reopenedStore)

        assertEquals("visitor", reopenedStorage.getKnownVisitorId(dottedIdentity))
        assertEquals(dottedIdentity, reopenedStorage.ownerIdentity)
        assertEquals("visitor", reopenedStorage.visitorId)
    }

    private fun identityKey(identity: String): String =
        VisitorStorageImpl.IDENTITY_KEY_PREFIX + identity

    private fun identityRows(): List<String> =
        dataStore.keys().filter { it.startsWith(VisitorStorageImpl.IDENTITY_KEY_PREFIX) }
}

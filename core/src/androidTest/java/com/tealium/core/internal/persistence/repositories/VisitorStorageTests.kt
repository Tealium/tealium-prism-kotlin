package com.tealium.core.internal.persistence.repositories

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.core.internal.persistence.stores.VisitorStorage
import com.tealium.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.tests.common.getDefaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class VisitorStorageTests {

    private val context: Application = ApplicationProvider.getApplicationContext() as Application
    private val config = getDefaultConfig(context)

    private lateinit var visitorStorage: VisitorStorage

    @Before
    fun setUp() {
        val dbProvider = InMemoryDatabaseProvider(config)
        val modulesRepository = SQLModulesRepository(dbProvider)
        val moduleStoreProvider = ModuleStoreProviderImpl(dbProvider, modulesRepository)
        val dataStore = moduleStoreProvider.getSharedDataStore()

        visitorStorage = VisitorStorageImpl(dataStore)
    }

    @Test
    fun visitorId_Returns_Null_When_No_Entry() {
        assertNull(visitorStorage.visitorId)
    }

    @Test
    fun currentIdentity_Returns_Null_When_No_Entry() {
        assertNull(visitorStorage.currentIdentity)
    }

    @Test
    fun visitorId_Returns_NewVisitorId_After_ChangingVisitor() {
        visitorStorage.changeVisitor("visitor")

        assertEquals("visitor", visitorStorage.visitorId)
    }

    @Test
    fun currentIdentity_Returns_NewIdentity_After_ChangingVisitor() {
        visitorStorage.changeVisitor("visitor", "identity")

        assertEquals("identity", visitorStorage.currentIdentity)
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
    fun changeVisitor_Replaces_Only_VisitorId_And_Associates_With_CurrentIdentity() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeVisitor("new_visitor")

        assertEquals("new_visitor", visitorStorage.visitorId)
        assertEquals("identity", visitorStorage.currentIdentity)
    }

    @Test
    fun changeVisitor_Replaces_Only_VisitorId_And_Associates_OnlyLatest_With_CurrentIdentity() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeVisitor("new_visitor")

        assertEquals("new_visitor", visitorStorage.getKnownVisitorId("identity"))
    }

    @Test
    fun changeVisitor_Replaces_Both_VisitorId_And_Identity() {
        visitorStorage.changeVisitor("visitor", "identity")
        assertEquals("visitor", visitorStorage.visitorId)
        assertEquals("identity", visitorStorage.currentIdentity)

        visitorStorage.changeVisitor("new_visitor", "new_identity")
        assertEquals("new_visitor", visitorStorage.visitorId)
        assertEquals("new_identity", visitorStorage.currentIdentity)
    }

    @Test
    fun changeIdentity_Replaces_Only_Identity() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeIdentity("new_identity")
        assertEquals("visitor", visitorStorage.visitorId)
        assertEquals("new_identity", visitorStorage.currentIdentity)
    }

    @Test
    fun clear_Removes_All_VisitorData_And_Sets_New_VisitorId() {
        visitorStorage.changeVisitor("visitor", "identity")
        visitorStorage.changeVisitor("new_visitor", "new_identity")

        visitorStorage.clear("reset_visitor")

        assertEquals("reset_visitor", visitorStorage.visitorId)
        assertNull(visitorStorage.currentIdentity)
        assertNull(visitorStorage.getKnownVisitorId("identity"))
        assertNull(visitorStorage.getKnownVisitorId("new_identity"))
    }
}
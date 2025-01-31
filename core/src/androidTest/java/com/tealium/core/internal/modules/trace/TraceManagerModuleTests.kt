package com.tealium.core.internal.modules.trace

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.core.internal.persistence.repositories.SQLKeyValueRepository
import com.tealium.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.core.internal.persistence.stores.ModuleStore
import com.tealium.tests.common.getDefaultConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TraceManagerModuleTests {

    private lateinit var app: Application
    private lateinit var dataStore: DataStore
    private lateinit var trace: TraceManagerModule
    private lateinit var dispatchContext: DispatchContext

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()

        // default to external events
        dispatchContext =
            DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

        val dbProvider = InMemoryDatabaseProvider(getDefaultConfig(app))
        val modulesRepository = SQLModulesRepository(dbProvider)
        val id = modulesRepository.registerModule(TraceManagerModule.Factory.id)
        val repository = SQLKeyValueRepository(dbProvider, id)

        dataStore = ModuleStore(repository)

        trace = TraceManagerModule(
            dataStore, mockk()
        )
    }

    @Test
    fun join_Adds_Trace_Id_To_Storage() {
        trace.join("12345")

        assertEquals("12345", dataStore.getString(Dispatch.Keys.TEALIUM_TRACE_ID))
    }

    @Test
    fun leave_Removes_Trace_Id_From_Storage() {
        dataStore.edit()
            .put(Dispatch.Keys.TEALIUM_TRACE_ID, "12345", Expiry.FOREVER)
            .commit()

        trace.leave()

        assertNull(dataStore.get(Dispatch.Keys.TEALIUM_TRACE_ID))
    }

    @Test
    fun collect_Returns_Trace_Id_In_DataObject_When_Trace_Joined() {
        trace.join("12345")

        val data = trace.collect(dispatchContext)

        assertEquals("12345", data.getString(Dispatch.Keys.TEALIUM_TRACE_ID))
    }

    @Test
    fun collect_Returns_Empty_Object_After_Leaving_Trace() {
        trace.join("12345")

        trace.leave()
        val data = trace.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, data)
    }

    @Test
    fun collect_Returns_Empty_Object_When_Trace_Not_Joined() {
        val data = trace.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, data)
    }

    @Test
    fun collect_Returns_Empty_Object_When_Source_Is_Trace_Module() {
        trace.join("12345")

        val dispatchContext = dispatchContext.copy(
            DispatchContext.Source.module(TraceManagerModule::class.java)
        )
        val data = trace.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, data)
    }
}
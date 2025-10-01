package com.tealium.prism.core.api.tracking

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.Module
import org.junit.Assert.*
import org.junit.Test

class DispatchContextTest {

    @Test
    fun getters_Return_Provided_Parameters() {
        val source = DispatchContext.Source.application()
        val initialData = DataObject.EMPTY_OBJECT
        val dispatchContext = DispatchContext(source, initialData)

        assertNotNull(dispatchContext)
        assertEquals(source, dispatchContext.source)
        assertEquals(initialData, dispatchContext.initialData)
    }

    @Test
    fun isFromApplication_Returns_True_When_Source_Is_Application() {
        val source = DispatchContext.Source.application()

        assertTrue(source.isFromApplication())
    }

    @Test
    fun isFromApplication_Returns_False_When_Source_Is_Module() {
        val source = DispatchContext.Source.module(TestModule::class.java)

        assertFalse(source.isFromApplication())
    }

    @Test
    fun isFromModule_Returns_True_When_Module_Class_Is_Same() {
        val source = DispatchContext.Source.module(TestModule::class.java)

        assertTrue(source.isFromModule(TestModule::class.java))
    }

    @Test
    fun isFromModule_Returns_False_When_Module_Class_Is_Different() {
        val source = DispatchContext.Source.module(TestModule::class.java)

        assertFalse(source.isFromModule(DifferentTestModule::class.java))
    }

    @Test
    fun isFromModule_Returns_False_When_Provided_Class_Is_Generic() {
        val source = DispatchContext.Source.module(TestModule::class.java)

        assertFalse(source.isFromModule(Module::class.java))
        assertFalse(source.isFromModule(Collector::class.java))
        assertFalse(source.isFromModule(Dispatcher::class.java))
    }

    abstract class TestModule : Module
    abstract class DifferentTestModule : Module
}
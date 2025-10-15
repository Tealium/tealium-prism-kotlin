package com.tealium.prism.core.internal.modules

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.tests.common.TestModuleFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleDiscoveryServiceTests {

    val app: Application = ApplicationProvider.getApplicationContext() as Application

    @Test
    fun discover_Returns_ModuleFactory_Created_Through_NoArgs_Constructor() {
        val factories = ModuleDiscoveryService.discover(app)

        val constructedFactory = factories.find { it.moduleType == "constructor" }
        assertNotNull(constructedFactory)
        assertTrue(constructedFactory is ConstructorModuleFactory)
    }

    @Test
    fun discover_Returns_ModuleFactory_Defined_As_Kotlin_Object() {
        val factories = ModuleDiscoveryService.discover(app)

        val objectFactory = factories.find { it.moduleType == "object" }
        assertNotNull(objectFactory)
        assertSame(ObjectModuleFactory, objectFactory)
    }

    @Test
    fun discover_Returns_ModuleFactory_Defined_As_Kotlin_Companion_Object() {
        val factories = ModuleDiscoveryService.discover(app)

        val companionFactory = factories.find { it.moduleType == "companion" }
        assertNotNull(companionFactory)
        assertSame(CompanionModuleFactory.Companion, companionFactory)
    }

    @Test
    fun discover_Returns_ModuleFactory_Defined_As_Class_Field() {
        val factories = ModuleDiscoveryService.discover(app)

        val fieldFactory = factories.find { it.moduleType == "field" }
        assertNotNull(fieldFactory)
        assertSame(FieldModuleFactory.FACTORY, fieldFactory)
    }

    @Test
    fun discover_Does_Not_Return_ModuleFactory_When_Class_Not_Found() {
        val factories = ModuleDiscoveryService.discover(app)

        val missingFactory = factories.find { it.moduleType == "missing" }
        assertNull(missingFactory)
    }
}

class ConstructorModuleFactory(delegateFactory: ModuleFactory): ModuleFactory by delegateFactory {
    constructor(): this(TestModuleFactory("constructor"))
}
object ObjectModuleFactory: ModuleFactory {
    private val delegateFactory = TestModuleFactory("object")

    override val moduleType: String = delegateFactory.moduleType

    override fun create(
        moduleId: String,
        context: TealiumContext,
        configuration: DataObject
    ): Module? = delegateFactory.create(moduleId, context, configuration)
}
class CompanionModuleFactory {
    companion object: ModuleFactory {
        private val delegateFactory = TestModuleFactory("companion")

        override val moduleType: String = delegateFactory.moduleType

        override fun create(
            moduleId: String,
            context: TealiumContext,
            configuration: DataObject
        ): Module? = delegateFactory.create(moduleId, context, configuration)
    }
}
class FieldModuleFactory {
    companion object {
        @JvmField
        val FACTORY = TestModuleFactory("field")
    }
}
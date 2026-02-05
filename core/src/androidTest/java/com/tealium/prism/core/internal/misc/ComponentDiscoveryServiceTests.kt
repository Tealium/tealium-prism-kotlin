package com.tealium.prism.core.internal.misc

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.barriers.ConfigurableBarrier
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.tests.common.TestModuleFactory
import org.junit.Assert
import org.junit.Test

class ComponentDiscoveryServiceTests {

    val app: Application = ApplicationProvider.getApplicationContext() as Application

    @Test
    fun discoverModules_Returns_ModuleFactory_Created_Through_NoArgs_Constructor() {
        val factories = ComponentDiscoveryService.discoverModules(app)

        val constructedFactory = factories.find { it.moduleType == "constructor" }
        Assert.assertNotNull(constructedFactory)
        Assert.assertTrue(constructedFactory is ConstructorModuleFactory)
    }

    @Test
    fun discoverModules_Returns_ModuleFactory_Defined_As_Kotlin_Object() {
        val factories = ComponentDiscoveryService.discoverModules(app)

        val objectFactory = factories.find { it.moduleType == "object" }
        Assert.assertNotNull(objectFactory)
        Assert.assertSame(ObjectModuleFactory, objectFactory)
    }

    @Test
    fun discoverModules_Returns_ModuleFactory_Defined_As_Kotlin_Companion_Object() {
        val factories = ComponentDiscoveryService.discoverModules(app)

        val companionFactory = factories.find { it.moduleType == "companion" }
        Assert.assertNotNull(companionFactory)
        Assert.assertSame(CompanionModuleFactory.Companion, companionFactory)
    }

    @Test
    fun discoverModules_Returns_ModuleFactory_Defined_As_Class_Field() {
        val factories = ComponentDiscoveryService.discoverModules(app)

        val fieldFactory = factories.find { it.moduleType == "field" }
        Assert.assertNotNull(fieldFactory)
        Assert.assertSame(FieldModuleFactory.Companion.FACTORY, fieldFactory)
    }

    @Test
    fun discoverModules_Does_Not_Return_ModuleFactory_When_Class_Not_Found() {
        val factories = ComponentDiscoveryService.discoverModules(app)

        val missingFactory = factories.find { it.moduleType == "missing" }
        Assert.assertNull(missingFactory)
    }

    @Test
    fun discoverBarriers_Returns_BarrierFactory_Created_Through_NoArgs_Constructor() {
        val factories = ComponentDiscoveryService.discoverBarriers(app)

        val constructedFactory = factories.find { it.id == "constructor" }
        Assert.assertNotNull(constructedFactory)
        Assert.assertTrue(constructedFactory is ConstructorBarrierFactory)
    }

    @Test
    fun discoverBarriers_Returns_BarrierFactory_Defined_As_Kotlin_Object() {
        val factories = ComponentDiscoveryService.discoverBarriers(app)

        val objectFactory = factories.find { it.id == "object" }
        Assert.assertNotNull(objectFactory)
        Assert.assertSame(ObjectBarrierFactory, objectFactory)
    }

    @Test
    fun discoverBarriers_Returns_BarrierFactory_Defined_As_Kotlin_Companion_Object() {
        val factories = ComponentDiscoveryService.discoverBarriers(app)

        val companionFactory = factories.find { it.id == "companion" }
        Assert.assertNotNull(companionFactory)
        Assert.assertSame(CompanionBarrierFactory.Companion, companionFactory)
    }

    @Test
    fun discoverBarriers_Returns_BarrierFactory_Defined_As_Class_Field() {
        val factories = ComponentDiscoveryService.discoverBarriers(app)

        val fieldFactory = factories.find { it.id == "field" }
        Assert.assertNotNull(fieldFactory)
        Assert.assertSame(FieldBarrierFactory.Companion.FACTORY, fieldFactory)
    }

    @Test
    fun discoverBarriers_Does_Not_Return_BarrierFactory_When_Class_Not_Found() {
        val factories = ComponentDiscoveryService.discoverBarriers(app)

        val missingFactory = factories.find { it.id == "missing" }
        Assert.assertNull(missingFactory)
    }

    @Test
    fun discoverBarriers_Returns_NonEmpty_List_When_Barriers_Declared_In_Manifest() {
        val factories = ComponentDiscoveryService.discoverBarriers(app)

        Assert.assertTrue(factories.isNotEmpty())
    }
}

// ModuleFactories

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

// BarrierFactories

class ConstructorBarrierFactory(
    delegateFactory: BarrierFactory = TestBarrierFactory("constructor")
) : BarrierFactory by delegateFactory

object ObjectBarrierFactory : BarrierFactory {
    private val delegateFactory = TestBarrierFactory("object")
    override val id: String get() = delegateFactory.id
    override fun defaultScopes(): Set<BarrierScope> = delegateFactory.defaultScopes()
    override fun create(context: TealiumContext, configuration: DataObject): ConfigurableBarrier =
        delegateFactory.create(context, configuration)
}

class CompanionBarrierFactory {
    companion object : BarrierFactory {
        private val delegateFactory = TestBarrierFactory("companion")
        override val id: String get() = delegateFactory.id
        override fun defaultScopes(): Set<BarrierScope> = delegateFactory.defaultScopes()
        override fun create(context: TealiumContext, configuration: DataObject): ConfigurableBarrier =
            delegateFactory.create(context, configuration)
    }
}

class FieldBarrierFactory {
    companion object {
        @JvmField
        val FACTORY: BarrierFactory = TestBarrierFactory("field")
    }
}

internal class TestBarrierFactory(
    override val id: String,
    private val defaultScopes: Set<BarrierScope> = emptySet()
) : BarrierFactory {
    override fun defaultScopes(): Set<BarrierScope> = defaultScopes
    override fun create(context: TealiumContext, configuration: DataObject): ConfigurableBarrier =
        object : ConfigurableBarrier {
            override val id: String get() = this@TestBarrierFactory.id
            override fun onState(dispatcherId: String) = Observables.just(BarrierState.Open)
        }
}
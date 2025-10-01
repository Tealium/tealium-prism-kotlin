package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.settings.BarrierSettings
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BarrierManagerTests {

    private lateinit var barrierManager: BarrierManager
    private lateinit var settings: StateSubject<Map<String, BarrierSettings>>
    private val openBarrier = barrier("barrier1", Observables.just(BarrierState.Open))
    private val closedBarrier = barrier("barrier2", Observables.just(BarrierState.Closed))

    @Before
    fun setUp() {
        settings = Observables.stateSubject(
            listOf(
                BarrierSettings(openBarrier.id, setOf(BarrierScope.All)),
                BarrierSettings(closedBarrier.id, setOf(BarrierScope.Dispatcher("dispatcher")))
            ).associateBy { it.barrierId }
        )
        barrierManager = BarrierManager(settings)
    }

    @Test
    fun initializeBarriers_Creates_Barriers_From_Factories() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)

        barrierManager.barriers.subscribe(verifier)
        barrierManager.initializeBarriers(
            listOf(
                barrierFactory(openBarrier),
                barrierFactory(closedBarrier)
            ), mockk(), emptyList()
        )

        verify {
            verifier.invoke(match {
                it.containsBarrier(openBarrier)
                        && it.containsBarrier(closedBarrier)
            })
        }
    }

    @Test
    fun initializeBarriers_Creates_Barriers_With_Configuration_From_Settings() {
        val config = DataObject.create { put("key", "value") }
        val mockFactory = spyk(barrierFactory(openBarrier))

        settings.onNext(mapOf(openBarrier.id to BarrierSettings(openBarrier.id, setOf(), config)))
        barrierManager.initializeBarriers(listOf(mockFactory))

        verify {
            mockFactory.create(any(), config)
        }
    }

    @Test
    fun initializeBarriers_Creates_Barriers_With_Empty_Config_If_Not_Found_In_Settings() {
        val mockFactory = spyk(barrierFactory(openBarrier))

        barrierManager.initializeBarriers(listOf(mockFactory))

        verify {
            mockFactory.create(any(), DataObject.EMPTY_OBJECT)
        }
    }

    @Test
    fun initializeBarriers_Scopes_Barriers_To_Scopes_From_Settings_When_Available() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val barrier = barrier("default", Observables.just(BarrierState.Open))
        val defaultScope = setOf(BarrierScope.Dispatcher("dispatcher"))

        settings.onNext(mapOf(barrier.id to BarrierSettings(barrier.id, emptySet())))
        barrierManager.initializeBarriers(listOf(barrierFactory(barrier, defaultScope)))
        barrierManager.barriers.subscribe(verifier)

        verify {
            verifier.invoke(match { it.contains(barrier to emptySet()) })
        }
    }

    @Test
    fun initializeBarriers_Scopes_Barriers_To_Default_When_No_Scopes_In_Settings() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val barrier = barrier("barrier", Observables.just(BarrierState.Open))
        val defaultScope = setOf(BarrierScope.Dispatcher("dispatcher"))

        barrierManager.initializeBarriers(listOf(barrierFactory(barrier, defaultScope)))
        barrierManager.barriers.subscribe(verifier)

        verify {
            verifier.invoke(match { it.contains(barrier to defaultScope) })
        }
    }

    @Test
    fun initializeBarriers_Scopes_Barriers_To_All_When_No_Settings_Or_Default_Scopes() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val barrier = barrier("barrier", Observables.just(BarrierState.Open))

        barrierManager.initializeBarriers(listOf(barrierFactory(barrier)))
        barrierManager.barriers.subscribe(verifier)

        verify {
            verifier.invoke(match { it.contains(barrier to setOf(BarrierScope.All)) })
        }
    }

    @Test
    fun initializeBarriers_Adds_Default_Barriers_With_Provided_Default_Scopes_When_Not_Given_In_Factories() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val defaultBarrier = barrier("default", Observables.just(BarrierState.Open))
        val defaultScope = setOf(BarrierScope.Dispatcher("dispatcher"))

        barrierManager.initializeBarriers(
            emptyList(), mockk(),
            listOf(barrierFactory(defaultBarrier, defaultScope))
        )
        barrierManager.barriers.subscribe(verifier)

        verify {
            verifier.invoke(match { it.contains(defaultBarrier to defaultScope) })
        }
    }

    @Test
    fun initializeBarriers_Adds_Default_Barriers_Scoped_To_All_When_No_Default_Or_Settings() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val defaultBarrier = barrier("default", Observables.just(BarrierState.Open))

        barrierManager.initializeBarriers(
            emptyList(), mockk(),
            defaultBarrierFactories = listOf(barrierFactory(defaultBarrier)),
        )
        barrierManager.barriers.subscribe(verifier)

        verify {
            verifier.invoke(match { it.contains(defaultBarrier to setOf(BarrierScope.All)) })
        }
    }

    @Test
    fun initializeBarriers_Ignores_Latest_Barrier_When_Ids_Clash() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val duplicateBarrier = barrier(openBarrier.id, Observables.just(BarrierState.Open))

        barrierManager.initializeBarriers(
            listOf(
                barrierFactory(openBarrier),
                barrierFactory(duplicateBarrier)
            )
        )
        barrierManager.barriers.subscribe(verifier)

        verify {
            verifier.invoke(match {
                it.containsBarrier(openBarrier)
                        && !it.containsBarrier(duplicateBarrier)
            })
        }
    }

    @Test
    fun registerScopedBarrier_Adds_Barrier_To_All_Barriers() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val barrier = barrier("barrier", Observables.just(BarrierState.Open))

        barrierManager.barriers.subscribe(verifier)
        barrierManager.registerScopedBarrier(barrier, setOf(BarrierScope.All))

        verify {
            verifier.invoke(match { it.containsBarrier(barrier) })
        }
    }

    @Test
    fun registerScopedBarrier_Adds_Barrier_With_Supplied_Scopes() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val barrier = barrier("barrier", Observables.just(BarrierState.Open))
        val scope = setOf(BarrierScope.Dispatcher("dispatcher"))

        barrierManager.barriers.subscribe(verifier)
        barrierManager.registerScopedBarrier(barrier, scope)

        verify {
            verifier.invoke(match {
                it.contains(barrier to scope)
            })
        }
    }

    @Test
    fun registerScopedBarrier_Updates_Barrier_With_Supplied_Scopes() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val barrier = barrier("barrier", Observables.just(BarrierState.Open))

        barrierManager.barriers.subscribe(verifier)
        barrierManager.registerScopedBarrier(barrier, setOf(BarrierScope.All))
        barrierManager.registerScopedBarrier(
            barrier,
            setOf(BarrierScope.Dispatcher("dispatcher"))
        )

        verify {
            verifier.invoke(match {
                !it.contains(barrier to setOf(BarrierScope.All))
                        && it.contains(barrier to setOf(BarrierScope.Dispatcher("dispatcher")))
            })
        }
    }

    @Test
    fun unregisterScopedBarrier_Removes_Previously_Registered_Barrier() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        val barrier = barrier("barrier", Observables.just(BarrierState.Open))

        barrierManager.initializeBarriers(emptyList())
        barrierManager.barriers.subscribe(verifier)
        barrierManager.registerScopedBarrier(barrier, setOf(BarrierScope.All))
        barrierManager.unregisterScopedBarrier(barrier)

        verifyOrder {
            verifier.invoke(match { it.containsBarrier(barrier) })
            verifier.invoke(match { it.isEmpty() })
        }
    }

    @Test
    fun unregisterScopedBarrier_Does_Not_Remove_Config_Registered_Barrier() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)

        barrierManager.initializeBarriers(
            listOf(barrierFactory(openBarrier)),
            mockk(),
            defaultBarrierFactories = listOf(barrierFactory(openBarrier))
        )
        barrierManager.unregisterScopedBarrier(openBarrier)
        barrierManager.barriers.subscribe(verifier)

        verify {
            verifier.invoke(match { it.containsBarrier(openBarrier) })
        }
    }

    @Test
    fun updatedSdkSettings_Are_Passed_Onto_ConfigurableBarriers() {
        val mockBarrier = spyk(openBarrier)
        val newConfig = DataObject.create { put("key", "value") }
        val newSettings = BarrierSettings(mockBarrier.id, setOf(), newConfig)

        barrierManager.initializeBarriers(listOf(barrierFactory(mockBarrier)))
        settings.onNext(mapOf(mockBarrier.id to newSettings))

        verify {
            mockBarrier.updateConfiguration(newConfig)
        }
    }

    @Test
    fun updatedSdkSettings_Are_Not_Passed_Onto_Programmatically_Registered_Barriers() {
        val mockBarrier = spyk(openBarrier)
        val newConfig = DataObject.create { put("key", "value") }
        val newSettings = BarrierSettings(mockBarrier.id, setOf(), newConfig)

        barrierManager.registerScopedBarrier(mockBarrier, setOf(BarrierScope.All))
        settings.onNext(
            mapOf(mockBarrier.id to newSettings)
        )

        verify(inverse = true) {
            mockBarrier.updateConfiguration(newConfig)
        }
    }

    @Test
    fun shutdown_Removes_All_Barriers() {
        val verifier = mockk<(List<ScopedBarrier>) -> Unit>(relaxed = true)
        barrierManager.initializeBarriers(listOf(barrierFactory(openBarrier)))
        barrierManager.registerScopedBarrier(
            barrier("registered", Observables.just(BarrierState.Open)), setOf(BarrierScope.All)
        )

        barrierManager.barriers.subscribe(verifier)
        barrierManager.shutdown()

        verify {
            verifier.invoke(emptyList())
        }
    }

    /**
     * Convenience to avoid mocking inline in the tests, and to avoid mocking the regular default barriers
     * in tests.
     */
    private fun BarrierManager.initializeBarriers(factories: List<BarrierFactory>) =
        initializeBarriers(factories, mockk(), emptyList())
}
package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.settings.CoreSettings
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.TealiumDispatchType
import com.tealium.prism.core.internal.persistence.repositories.VolatileQueueRepository
import com.tealium.prism.core.internal.settings.CoreSettingsImpl
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test

class QueueMetricsTests {

    @RelaxedMockK
    private lateinit var verifier: (Int) -> Unit

    private lateinit var queueManager: QueueManager
    private lateinit var processors: Subject<Set<String>>
    private lateinit var settings: Subject<CoreSettings>
    private lateinit var enqueued: Subject<Set<String>>
    private lateinit var queue: MutableMap<String, MutableSet<Dispatch>>

    private lateinit var queueMetrics: QueueMetrics

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        queue = mutableMapOf()
        settings = Observables.stateSubject(CoreSettingsImpl())
        processors = Observables.stateSubject(setOf("dispatcher_1", "dispatcher_2"))
        enqueued = Observables.publishSubject()
        queueManager = QueueManagerImpl(
            VolatileQueueRepository(queue),
            settings,
            processors,
            _enqueuedDispatches = enqueued,
            logger = SystemLogger
        )

        // store one dispatch by default
        queueManager.storeDispatches(listOf(createDispatch("1")), setOf("dispatcher_1"))

        queueMetrics = QueueMetricsImpl(queueManager)
    }

    @Test
    fun queueSizePendingDispatch_Emits_Initial_QueueSize_For_Given_Processor() {
        queueMetrics.queueSizePendingDispatch("dispatcher_1")
            .subscribe(verifier)

        verify {
            verifier.invoke(1)
        }
    }

    @Test
    fun queueSizePendingDispatch_Emits_Different_Initial_QueueSizes_For_Different_Processors() {
        val verifier2 = mockk<(Int) -> Unit>(relaxed = true)
        queueManager.storeDispatches(
            listOf(createDispatch("2"), createDispatch("2")),
            setOf("dispatcher_2")
        )

        queueMetrics.queueSizePendingDispatch("dispatcher_1")
            .subscribe(verifier)
        queueMetrics.queueSizePendingDispatch("dispatcher_2")
            .subscribe(verifier2)

        verify {
            verifier.invoke(1)
            verifier2.invoke(2)
        }
    }

    @Test
    fun queueSizePendingDispatch_Does_Not_Emit_When_Dispatch_Enqueued_But_QueueSize_Is_Unchanged() {
        queueMetrics.queueSizePendingDispatch("dispatcher_1")
            .subscribe(verifier)
        enqueued.onNext(setOf("dispatcher_1"))
        enqueued.onNext(setOf("dispatcher_1"))
        enqueued.onNext(setOf("dispatcher_1"))

        verify(exactly = 1) {
            verifier.invoke(1)
        }
    }

    @Test
    fun queueSizePendingDispatch_Does_Not_Emit_When_QueueSize_Is_Unchanged_Even_With_Other_Changes() {
        queueMetrics.queueSizePendingDispatch("dispatcher_1")
            .subscribe(verifier)

        queueManager.storeDispatches(listOf(createDispatch("2")), setOf("dispatcher_2"))

        verify(exactly = 1) {
            verifier.invoke(1)
        }
    }

    @Test
    fun queueSizePendingDispatch_Emits_Reduced_Size_When_Dispatches_InFlight() {
        queueMetrics.queueSizePendingDispatch("dispatcher_1")
            .subscribe(verifier)

        queueManager.storeDispatches(listOf(createDispatch("2")), setOf("dispatcher_1"))
        queueManager.storeDispatches(listOf(createDispatch("3")), setOf("dispatcher_1"))

        queueManager.dequeueDispatches(1, "dispatcher_1")
        queueManager.dequeueDispatches(1, "dispatcher_1")
        queueManager.dequeueDispatches(1, "dispatcher_1")

        verifyOrder {
            // initial
            verifier.invoke(1)

            // enqueued
            verifier.invoke(2)
            verifier.invoke(3)

            // dequeued
            verifier.invoke(2)
            verifier.invoke(1)
            verifier.invoke(0)
        }
    }

    @Test
    fun queueSizePendingDispatch_Does_Not_Change_When_InFlight_Is_Processed() {
        queueMetrics.queueSizePendingDispatch("dispatcher_1")
            .subscribe(verifier)

        val dispatches = queueManager.dequeueDispatches(1, "dispatcher_1")
        queueManager.deleteDispatches(dispatches, "dispatcher_1")

        verify(ordering = Ordering.SEQUENCE) {
            // initial
            verifier.invoke(1)

            // dequeued + processed
            verifier.invoke(0)
        }
        confirmVerified(verifier)
    }

    @Test
    fun queueSizePendingDispatch_Emits_Zero_When_Processor_Not_Found() {
        queueMetrics.queueSizePendingDispatch("dispatcher_2")
            .subscribe(verifier)

        verify {
            verifier.invoke(0)
        }
    }

    @Test
    fun queueSizePendingDispatch_Emits_Updated_Value_When_Settings_Have_Updated() {
        queueManager.storeDispatches(listOf(createDispatch("2")), setOf("dispatcher_1"))
        queueMetrics.queueSizePendingDispatch("dispatcher_1")
            .subscribe(verifier)

        settings.onNext(CoreSettingsImpl(maxQueueSize = 1))

        verify {
            // initial
            verifier.invoke(2)
            // after resize
            verifier.invoke(1)
        }
    }

    private fun createDispatch(eventName: String = "event"): Dispatch =
        Dispatch.create(eventName, TealiumDispatchType.Event)
}
package com.tealium.prism.core.internal.barriers

import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.tests.common.TestDispatcher
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BatchingBarrierTests {

    @RelaxedMockK
    private lateinit var stateVerifier: (BarrierState) -> Unit

    @MockK
    private lateinit var queueMetrics: QueueMetrics

    private lateinit var dispatcher1: Dispatcher
    private lateinit var dispatcher2: Dispatcher
    private lateinit var dispatcher10: Dispatcher
    private lateinit var queueSize1: Subject<Int>
    private lateinit var queueSize2: Subject<Int>
    private lateinit var queueSize10: Subject<Int>
    private lateinit var dispatchers: StateSubject<List<Dispatcher>>

    private lateinit var batchingBarrier: BatchingBarrier

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        dispatcher1 = TestDispatcher("dispatcher1", dispatchLimit = 1)
        dispatcher2 = TestDispatcher("dispatcher2", dispatchLimit = 2)
        dispatcher10 = TestDispatcher("dispatcher10", dispatchLimit = 10)
        dispatchers = Observables.stateSubject(listOf(dispatcher1, dispatcher2, dispatcher10))
        queueSize1 = Observables.replaySubject(1)
        queueSize2 = Observables.replaySubject(1)
        queueSize10 = Observables.replaySubject(1)

        every { queueMetrics.queueSizePendingDispatch("dispatcher1") } returns queueSize1
        every { queueMetrics.queueSizePendingDispatch("dispatcher2") } returns queueSize2
        every { queueMetrics.queueSizePendingDispatch("dispatcher10") } returns queueSize10

        batchingBarrier = BatchingBarrier(queueMetrics, dispatchers, 1)
    }

    @Test
    fun onState_Prefers_Configured_Size_When_Less_Than_Dispatch_Limit() {
        queueSize2.onNext(1)
        setBatchSize(1) // dispatcher2 limit == 2; batchSize == 1

        batchingBarrier.onState("dispatcher2")
            .subscribe(stateVerifier)

        verify {
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun onState_Uses_Dispatcher_DispatchLimit_When_Configured_BatchSize_Is_Greater() {
        queueSize2.onNext(2)
        setBatchSize(10) // dispatcher2 limit == 2; batchSize == 10

        batchingBarrier.onState("dispatcher2")
            .subscribe(stateVerifier)

        verifyOrder {
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun onState_Uses_Dispatcher_DispatchLimit_When_No_BatchSize_Configured() {
        queueSize2.onNext(2)
        setBatchSize(null)

        batchingBarrier.onState("dispatcher2")
            .subscribe(stateVerifier)

        verify {
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun onState_Returns_Open_When_QueueSize_Is_Equal_To_Required_BatchSize() {
        queueSize1.onNext(1)

        batchingBarrier.onState("dispatcher1")
            .subscribe(stateVerifier)

        verify {
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun onState_Returns_Open_When_QueueSize_Is_Greater_Than_Required_BatchSize() {
        queueSize1.onNext(2)

        batchingBarrier.onState("dispatcher1")
            .subscribe(stateVerifier)

        verify {
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun onState_Returns_Closed_When_QueueSize_Is_Less_Than_Required_BatchSize() {
        queueSize1.onNext(0)

        batchingBarrier.onState("dispatcher1")
            .subscribe(stateVerifier)

        verify {
            stateVerifier.invoke(BarrierState.Closed)
        }
    }

    @Test
    fun onState_Opens_When_QueueSize_Becomes_Equal_To_Required_BatchSize() {
        queueSize1.onNext(0)

        batchingBarrier.onState("dispatcher1")
            .subscribe(stateVerifier)
        queueSize1.onNext(1)

        verifyOrder {
            stateVerifier.invoke(BarrierState.Closed)
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun onState_Closes_When_QueueSize_Becomes_Less_Than_Required_BatchSize() {
        queueSize1.onNext(1)

        batchingBarrier.onState("dispatcher1")
            .subscribe(stateVerifier)
        queueSize1.onNext(0)

        verifyOrder {
            stateVerifier.invoke(BarrierState.Open)
            stateVerifier.invoke(BarrierState.Closed)
        }
    }

    @Test
    fun onState_Opens_When_Required_BatchSize_Becomes_Less_Than_Or_Equal_To_Queue_Size() {
        queueSize10.onNext(5)
        setBatchSize(6)

        batchingBarrier.onState("dispatcher10")
            .subscribe(stateVerifier)
        setBatchSize(4)

        verifyOrder {
            stateVerifier.invoke(BarrierState.Closed)
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun onState_Remains_Open_While_Queue_Size_Is_Greater_Than_BatchSize_When_Foregrounded() {
        queueSize10.onNext(7)
        setBatchSize(3)

        batchingBarrier.onState("dispatcher10")
            .subscribe(stateVerifier)

        verify {
            stateVerifier.invoke(BarrierState.Open)
        }

        queueSize10.onNext(4)
        verify(inverse = true) {
            stateVerifier.invoke(BarrierState.Closed)
        }

        queueSize10.onNext(1)
        verify {
            stateVerifier.invoke(BarrierState.Closed)
        }
    }

    @Test
    fun updateConfiguration_Sets_BatchSize_To_Updated_Value() {
        setBatchSize(10)

        batchingBarrier.updateConfiguration(DataObject.create {
            put(BatchingBarrier.KEY_BATCH_SIZE, 5)
        })

        assertEquals(5, batchingBarrier.batchSize)
    }

    @Test
    fun updateConfiguration_Sets_BatchSize_To_Null_When_Omitted() {
        setBatchSize(10)

        batchingBarrier.updateConfiguration(DataObject.EMPTY_OBJECT)

        assertNull(batchingBarrier.batchSize)
    }

    @Test
    fun updateConfiguration_Coerces_Batch_Size_To_Be_Positive() {
        setBatchSize(1)
        assertEquals(1, batchingBarrier.batchSize)

        setBatchSize(0)
        assertEquals(1, batchingBarrier.batchSize)
        setBatchSize(-1)
        assertEquals(1, batchingBarrier.batchSize)
        setBatchSize(Int.MIN_VALUE)
        assertEquals(1, batchingBarrier.batchSize)
    }

    @Test
    fun dispatchers_Opens_Barriers_If_DispatchLimit_Reduced_When_Dispatchers_Updated() {
        queueSize10.onNext(5) // not met
        setBatchSize(null)

        batchingBarrier.onState("dispatcher10")
            .subscribe(stateVerifier)
        dispatchers.onNext(listOf(TestDispatcher("dispatcher10", dispatchLimit = 5)))

        verifyOrder {
            stateVerifier.invoke(BarrierState.Closed)
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun dispatchers_Recalculates_BatchSize_If_DispatchLimit_Updated_When_Dispatchers_Updated() {
        queueSize1.onNext(0) // not met
        setBatchSize(3)

        batchingBarrier.onState("dispatcher1")
            .subscribe(stateVerifier)
        dispatchers.onNext(listOf(TestDispatcher("dispatcher1", dispatchLimit = 5)))

        verify {
            stateVerifier.invoke(BarrierState.Closed)
        }
        verify(inverse = true) {
            stateVerifier.invoke(BarrierState.Open)
        }

        queueSize1.onNext(3) // limited to configured batchSize
        verify {
            stateVerifier.invoke(BarrierState.Open)
        }
    }

    @Test
    fun init_Coerces_Initial_Batch_Size_To_Be_Positive() {
        assertEquals(1, BatchingBarrier(queueMetrics, dispatchers, 1).batchSize)
        assertEquals(1, BatchingBarrier(queueMetrics, dispatchers, 0).batchSize)
        assertEquals(1, BatchingBarrier(queueMetrics, dispatchers, -1).batchSize)
        assertEquals(1, BatchingBarrier(queueMetrics, dispatchers, Int.MIN_VALUE).batchSize)
    }

    private fun setBatchSize(size: Int?) {
        if (size == null) {
            batchingBarrier.updateConfiguration(DataObject.EMPTY_OBJECT)
            return
        }

        batchingBarrier.updateConfiguration(DataObject.create {
            put(BatchingBarrier.KEY_BATCH_SIZE, size)
        })
    }
}
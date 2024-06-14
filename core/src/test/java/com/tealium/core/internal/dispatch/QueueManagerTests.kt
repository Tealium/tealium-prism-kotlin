package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.TealiumDispatchType
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import com.tealium.core.internal.observables.Subject
import com.tealium.core.internal.persistence.QueueRepository
import com.tealium.core.internal.persistence.days
import com.tealium.core.internal.settings.CoreSettings
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class QueueManagerTests {

    @RelaxedMockK
    private lateinit var queueRepository: QueueRepository

    private val dispatcher1 = "dispatcher_1"
    private val dispatcher2 = "dispatcher_2"
    private val dispatcher3 = "dispatcher_3"
    private val allProcessors = setOf(dispatcher1, dispatcher2, dispatcher3)
    private val dispatch1: Dispatch =
        Dispatch.create("test1", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)
    private val dispatch2: Dispatch =
        Dispatch.create("test2", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)
    private val dispatch3: Dispatch =
        Dispatch.create("test3", TealiumDispatchType.Event, TealiumBundle.EMPTY_BUNDLE)

    private lateinit var coreSettings: Subject<CoreSettings>
    private lateinit var processors: Subject<Set<String>>
    private lateinit var inFlightDispatches: StateSubject<Map<String, Set<Dispatch>>>
    private lateinit var enqueuedDispatches: Subject<Set<String>>

    private lateinit var queueManager: QueueManager

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        processors = Observables.replaySubject()
        inFlightDispatches = Observables.stateSubject(mapOf())
        enqueuedDispatches = Observables.replaySubject(1)
        coreSettings = Observables.publishSubject()

        queueManager = QueueManagerImpl(
            queueRepository, coreSettings, processors, inFlightDispatches, enqueuedDispatches
        )
    }

    @Test
    fun init_Removes_ProcessorQueues_For_MissingProcessors() {
        val processors = Observables.replaySubject<Set<String>>(1)
        processors.onNext(setOf(dispatcher1))
        queueManager = QueueManagerImpl(
            queueRepository, coreSettings, processors, inFlightDispatches, enqueuedDispatches
        )

        verify {
            queueRepository.deleteQueues(forProcessorsNotIn = setOf(dispatcher1))
        }
    }

    @Test
    fun processors_Removes_ProcessorQueues_When_Modules_Update() {
        queueManager = QueueManagerImpl(
            queueRepository, coreSettings, processors, inFlightDispatches, enqueuedDispatches
        )
        processors.onNext(setOf(dispatcher1))

        verify {
            queueRepository.deleteQueues(forProcessorsNotIn = setOf(dispatcher1))
        }
    }

    @Test
    fun processors_Removes_Inflight_For_Missing_Processors() {
        inFlightDispatches.onNext(mapOf(dispatcher1 to setOf(dispatch1, dispatch2, dispatch3)))
        val inFlightCount = mockk<(Int) -> Unit>(relaxed = true)

        queueManager = QueueManagerImpl(
            queueRepository, coreSettings, processors, inFlightDispatches, enqueuedDispatches
        )
        queueManager.inFlightCount(dispatcher1).subscribe(inFlightCount)
        processors.onNext(setOf(dispatcher2))

        verify {
            queueRepository.deleteQueues(forProcessorsNotIn = setOf(dispatcher2))
            inFlightCount(3) // init
            inFlightCount(0) // post-delete
        }
    }

    @Test
    fun storeDispatch_Stores_Dispatch_For_Each_Processor() {
        queueManager.storeDispatches(listOf(dispatch1), allProcessors)

        verify {
            queueRepository.storeDispatch(listOf(dispatch1), allProcessors)
        }
    }

    @Test
    fun storeDispatch_Stores_Multiple_Dispatch_For_Each_Processor() {
        queueManager.storeDispatches(listOf(dispatch1, dispatch2, dispatch3), allProcessors)

        verify {
            queueRepository.storeDispatch(listOf(dispatch1, dispatch2, dispatch3), allProcessors)
        }
    }

    @Test
    fun storeDispatch_Stores_Multiple_Dispatch_For_Single_Processor() {
        queueManager.storeDispatches(listOf(dispatch1, dispatch2, dispatch3), setOf(dispatcher1))

        verify {
            queueRepository.storeDispatch(
                listOf(dispatch1, dispatch2, dispatch3),
                setOf(dispatcher1)
            )
        }
        confirmVerified(queueRepository)
    }

    @Test
    fun storeDispatch_DoesNothing_When_EmptyDispatches_Or_Processors() {
        queueManager.storeDispatches(listOf(), setOf(dispatcher1))
        queueManager.storeDispatches(listOf(dispatch1), setOf())

        verify(inverse = true) {
            queueRepository.storeDispatch(any(), any())
        }
        confirmVerified(queueRepository)
    }

    @Test
    fun storeDispatch_Emits_EnqueuedDispatches() {
        val dispatchesEnqueued = mockk<(Set<String>) -> Unit>(relaxed = true)
        queueManager.enqueuedDispatchesForProcessors.subscribe(dispatchesEnqueued)

        queueManager.storeDispatches(listOf(dispatch1, dispatch2, dispatch3), setOf(dispatcher1))

        verify {
            dispatchesEnqueued(setOf(dispatcher1))
        }
    }

    @Test
    fun getQueuedDispatches_Returns_Dispatches_For_Processor() {
        every { queueRepository.getQueuedDispatches(1, any(), dispatcher1) } returns listOf(
            dispatch1
        )
        every { queueRepository.getQueuedDispatches(1, any(), dispatcher2) } returns listOf(
            dispatch2
        )
        every { queueRepository.getQueuedDispatches(1, any(), dispatcher3) } returns listOf(
            dispatch3
        )

        queueManager.getQueuedDispatches(1, dispatcher1)
        queueManager.getQueuedDispatches(1, dispatcher2)
        queueManager.getQueuedDispatches(1, dispatcher3)

        verify {
            queueRepository.getQueuedDispatches(1, any(), dispatcher1)
            queueRepository.getQueuedDispatches(1, any(), dispatcher2)
            queueRepository.getQueuedDispatches(1, any(), dispatcher3)
        }
    }

    @Test
    fun getQueuedDispatches_Returns_Limited_Dispatches_For_Processor() {
        every { queueRepository.getQueuedDispatches(any(), any(), dispatcher1) } returns listOf(
            dispatch1
        )

        queueManager.getQueuedDispatches(1, dispatcher1)
        queueManager.getQueuedDispatches(2, dispatcher1)
        queueManager.getQueuedDispatches(3, dispatcher1)

        verify {
            queueRepository.getQueuedDispatches(1, any(), dispatcher1)
            queueRepository.getQueuedDispatches(2, any(), dispatcher1)
            queueRepository.getQueuedDispatches(3, any(), dispatcher1)
        }
    }

    @Test
    fun getQueuedDispatches_Excludes_Inflight_Dispatches() {
        every { queueRepository.getQueuedDispatches(any(), any(), dispatcher1) } answers {
            listOf(dispatch1)
        } andThenAnswer { listOf(dispatch2) } andThenAnswer { listOf(dispatch3) }

        queueManager.getQueuedDispatches(1, dispatcher1)
        queueManager.getQueuedDispatches(1, dispatcher1)
        queueManager.getQueuedDispatches(1, dispatcher1)

        verify {
            queueRepository.getQueuedDispatches(1, emptySet(), dispatcher1)
            queueRepository.getQueuedDispatches(1, setOf(dispatch1), dispatcher1)
            queueRepository.getQueuedDispatches(1, setOf(dispatch1, dispatch2), dispatcher1)
        }
    }

    @Test
    fun getQueuedDispatches_Updates_Inflight_Count() {
        val inFlightCount = mockk<(Int) -> Unit>(relaxed = true)
        every { queueRepository.getQueuedDispatches(any(), any(), dispatcher1) } answers {
            listOf(dispatch1)
        } andThenAnswer { listOf(dispatch2, dispatch3) }

        queueManager.inFlightCount(dispatcher1).subscribe(inFlightCount)

        queueManager.getQueuedDispatches(1, dispatcher1)
        queueManager.getQueuedDispatches(2, dispatcher1)

        verify {
            inFlightCount(1)
            inFlightCount(3)
        }
    }

    @Test
    fun deleteDispatches_Removes_Dispatches_FromRepository_For_Specific_Processor() {
        queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)

        verify {
            queueRepository.deleteDispatches(listOf(dispatch1), dispatcher1)
        }
        confirmVerified(queueRepository)
    }

    @Test
    fun deleteDispatches_Removes_Dispatches_From_Inflight_Count() {
        inFlightDispatches.onNext(mapOf(dispatcher1 to setOf(dispatch1)))
        val inFlightCount = mockk<(Int) -> Unit>(relaxed = true)

        queueManager.inFlightCount(dispatcher1).subscribe(inFlightCount)

        queueManager.deleteDispatches(listOf(dispatch1), dispatcher1)

        verify {
            inFlightCount(1) // init
            inFlightCount(0) // post-delete
        }
    }

    @Test
    fun deleteAllDispatches_Removes_Dispatches_From_Repository_For_Processor() {
        queueManager.deleteAllDispatches(dispatcher1)

        verify {
            queueRepository.deleteAllDispatches(dispatcher1)
        }
        confirmVerified(queueRepository)
    }

    @Test
    fun deleteAllDispatches_Removes_Dispatches_From_Inflight_Count() {
        inFlightDispatches.onNext(mapOf(dispatcher1 to setOf(dispatch1, dispatch2)))
        val inFlightCount = mockk<(Int) -> Unit>(relaxed = true)

        queueManager.inFlightCount(dispatcher1).subscribe(inFlightCount)

        queueManager.deleteAllDispatches(dispatcher1)

        verify {
            inFlightCount(2) // init
            inFlightCount(0) // post-delete
        }
    }

    @Test
    fun settings_Resizes_OnUpdate() {
        coreSettings.onNext(CoreSettings(maxQueueSize = 100))

        verify {
            queueRepository.resize(100)
        }
    }

    @Test
    fun settings_UpdatesExpiration_OnUpdate() {
        coreSettings.onNext(CoreSettings(expiration = 10))

        verify {
            queueRepository.setExpiration(10.days)
        }
    }
}
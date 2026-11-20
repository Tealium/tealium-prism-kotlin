package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.misc.SingleThreadedScheduler
import com.tealium.tests.common.AsyncTransformer
import com.tealium.tests.common.PendingTransformation
import com.tealium.tests.common.Recorder
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestTransformer
import com.tealium.tests.common.awaitQueuedTasks
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class TransformerCoordinatorDisposalTests {

    @RelaxedMockK
    private lateinit var mockDispatch: Dispatch

    @RelaxedMockK
    private lateinit var onTransformed: (Dispatch?) -> Unit

    @RelaxedMockK
    private lateinit var onTransformedList: (List<Dispatch?>) -> Unit

    private lateinit var mockTransformer1: Transformer
    private lateinit var mockTransformer2: Transformer
    private lateinit var mockTransformer3: Transformer
    private lateinit var mockTransformer4: Transformer
    private lateinit var registeredTransformers: ReplaySubject<List<Transformer>>
    private lateinit var transformationsSettings: StateSubject<List<TransformationSettings>>
    private lateinit var transformerCoordinator: TransformerCoordinatorImpl
    private lateinit var tealiumExecutor: ScheduledExecutorService
    private lateinit var tealiumScheduler: Scheduler

    private val afterCollectors = TransformationSettings(
        "after_collectors", "transformer1", TransformationScope.AfterCollectors
    )
    private val allDispatchers = TransformationSettings(
        "all_dispatchers", "transformer2", TransformationScope.AllDispatchers
    )
    private val dispatcher1 = TransformationSettings(
        "dispatcher_1", "transformer3", TransformationScope.Dispatchers("dispatcher_1")
    )
    private val dispatcher1And2 = TransformationSettings(
        "dispatcher_1_and_2", "transformer1",
        TransformationScope.Dispatchers("dispatcher_1", "dispatcher_2"),
    )
    private val defaultTransformers: List<Transformer>
        get() = listOf(mockTransformer1, mockTransformer2, mockTransformer3, mockTransformer4)

    private val defaultTransformationSettings: List<TransformationSettings> = listOf(
        afterCollectors,
        allDispatchers,
        dispatcher1,
        dispatcher1And2,
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        mockTransformer1 = TestTransformer.mock("transformer1")
        mockTransformer2 = TestTransformer.mock("transformer2")
        mockTransformer3 = TestTransformer.mock("transformer3")
        mockTransformer4 = TestTransformer.mock("transformer4")

        registeredTransformers = Observables.replaySubject(1)
        registeredTransformers.onNext(defaultTransformers)

        transformationsSettings = Observables.stateSubject(defaultTransformationSettings)

        transformerCoordinator = TransformerCoordinatorImpl(
            registeredTransformers, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )

        val threadFactory = SingleThreadedScheduler.SingleThreadFactory("tealium-test")
        tealiumExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory)
        tealiumScheduler = SingleThreadedScheduler(threadFactory, tealiumExecutor)
    }

    @After
    fun tearDown() {
        tealiumExecutor.shutdownNow()
    }

    /**
     * The transformers arrive with the settings pass that creates the module implementing them, which
     * can be after the dispatch has been handed over. The transformation must wait for them rather than
     * be skipped against a premature empty list.
     */
    @Test
    fun transform_Applies_The_Transformation_Arriving_With_The_Transformers() {
        registeredTransformers = Observables.replaySubject(1)
        transformerCoordinator = TransformerCoordinatorImpl(
            registeredTransformers, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )

        transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )

        verify(inverse = true) {
            mockTransformer1.applyTransformation(
                afterCollectors, mockDispatch, DispatchScope.AfterCollectors, any()
            )
        }

        registeredTransformers.onNext(defaultTransformers)

        verify {
            mockTransformer1.applyTransformation(
                afterCollectors, mockDispatch, DispatchScope.AfterCollectors, any()
            )
        }
    }

    /**
     * A dequeued batch shares one transformer snapshot: the gate on the transformers is paid once per
     * batch, not once per dispatch.
     */
    @Test
    fun transform_Resolves_Transformers_Once_When_Transforming_A_Batch() {
        val trackedTransformers = trackingSubscriptions(registeredTransformers)
        transformerCoordinator = TransformerCoordinatorImpl(
            trackedTransformers.observable, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )
        val dispatches = listOf<Dispatch>(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
        )

        transformerCoordinator.transform(
            dispatches, DispatchScope.Dispatcher("dispatcher_1"), onTransformedList
        )

        assertEquals(1, trackedTransformers.subscriptionCount)
        verify(exactly = 1) { onTransformedList(dispatches) }
    }

    /**
     * The snapshot taken for the batch is the one every dispatch in it is transformed against, even
     * when the transformers change again before the batch completes.
     */
    @Test
    fun transform_Applies_The_Same_Transformer_Snapshot_To_Every_Dispatch_In_A_Batch() {
        registeredTransformers = Observables.replaySubject(1)
        transformerCoordinator = TransformerCoordinatorImpl(
            registeredTransformers, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )
        val dispatches = listOf<Dispatch>(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
        )
        val scope = DispatchScope.Dispatcher("dispatcher_1")

        transformerCoordinator.transform(dispatches, scope, onTransformedList)
        registeredTransformers.onNext(listOf(mockTransformer3))
        registeredTransformers.onNext(defaultTransformers)

        dispatches.forEach { dispatch ->
            verify(exactly = 1) {
                mockTransformer3.applyTransformation(dispatcher1, dispatch, scope, any())
            }
            verify(exactly = 0) {
                mockTransformer2.applyTransformation(allDispatchers, dispatch, scope, any())
                mockTransformer1.applyTransformation(dispatcher1And2, dispatch, scope, any())
            }
        }
        verify(exactly = 1) { onTransformedList(dispatches) }
    }

    @Test
    fun transform_Unsubscribes_From_Transformers_When_Disposed_While_Waiting_For_Transformers() {
        val trackedTransformers = trackingSubscriptions(Observables.replaySubject<List<Transformer>>(1))
        transformerCoordinator = TransformerCoordinatorImpl(
            trackedTransformers.observable, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )

        val subscription = transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )
        subscription.dispose()

        assertEquals(1, trackedTransformers.subscriptionCount)
        assertTrue(trackedTransformers.allDisposed)
    }

    @Test
    fun transform_Unsubscribes_From_Transformers_When_Disposed_While_Waiting_For_Transformers_For_A_Batch() {
        val trackedTransformers = trackingSubscriptions(Observables.replaySubject<List<Transformer>>(1))
        transformerCoordinator = TransformerCoordinatorImpl(
            trackedTransformers.observable, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )

        val subscription = transformerCoordinator.transform(
            listOf(mockDispatch), DispatchScope.AfterCollectors, onTransformedList
        )
        subscription.dispose()

        assertEquals(1, trackedTransformers.subscriptionCount)
        assertTrue(trackedTransformers.allDisposed)
    }

    @Test
    fun transform_Does_Not_Apply_Transformations_When_Disposed_Before_Transformers_Are_Ready() {
        registeredTransformers = Observables.replaySubject(1)
        transformerCoordinator = TransformerCoordinatorImpl(
            registeredTransformers, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)
            .dispose()
        registeredTransformers.onNext(defaultTransformers)

        verify(exactly = 0) {
            mockTransformer1.applyTransformation(any(), any(), any(), any())
            onTransformed(any())
        }
    }

    @Test
    fun transform_Does_Not_Apply_Transformations_When_Disposed_Before_Transformers_Are_Ready_For_A_Batch() {
        registeredTransformers = Observables.replaySubject(1)
        transformerCoordinator = TransformerCoordinatorImpl(
            registeredTransformers, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )

        transformerCoordinator.transform(
            listOf(mockDispatch), DispatchScope.AfterCollectors, onTransformedList
        ).dispose()
        registeredTransformers.onNext(defaultTransformers)

        verify(exactly = 0) {
            mockTransformer1.applyTransformation(any(), any(), any(), any())
            onTransformedList(any())
        }
    }

    @Test
    fun transform_Does_Not_Call_Completion_When_Disposed_While_Transformations_Are_Running() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))

        val subscription = transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )
        assertEquals(1, asyncTransformer.pending.size)

        subscription.dispose()
        asyncTransformer.completeAll()

        verify(inverse = true) { onTransformed(any()) }
    }

    @Test
    fun transform_Does_Not_Call_Completion_When_Disposed_While_Transformations_Are_Running_For_A_Batch() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))
        val dispatches = listOf<Dispatch>(mockk(relaxed = true), mockk(relaxed = true))

        val subscription = transformerCoordinator.transform(
            dispatches, DispatchScope.AfterCollectors, onTransformedList
        )
        assertEquals(2, asyncTransformer.pending.size)

        subscription.dispose()
        asyncTransformer.completeAll()

        verify(inverse = true) { onTransformedList(any()) }
    }

    @Test
    fun transform_Returns_Disposed_Subscription_When_All_Transformers_Complete_Synchronously() {
        val single = transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )
        val batch = transformerCoordinator.transform(
            listOf(mockDispatch), DispatchScope.AfterCollectors, onTransformedList
        )
        val empty = transformerCoordinator.transform(
            emptyList(), DispatchScope.AfterCollectors, onTransformedList
        )

        assertTrue(single.isDisposed)
        assertTrue(batch.isDisposed)
        assertTrue(empty.isDisposed)
        verify(exactly = 1) { onTransformed(mockDispatch) }
        verify(exactly = 1) { onTransformedList(listOf(mockDispatch)) }
    }

    @Test
    fun transform_Returns_Active_Subscription_When_Transformations_Are_Running() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))

        val subscription = transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )
        assertFalse(subscription.isDisposed)

        asyncTransformer.completeAll()

        assertTrue(subscription.isDisposed)
        verify(exactly = 1) { onTransformed(mockDispatch) }
    }

    @Test
    fun transform_Starts_Every_Dispatch_In_A_Batch_Before_Any_Completes() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))
        val dispatches = listOf<Dispatch>(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
        )

        transformerCoordinator.transform(
            dispatches, DispatchScope.AfterCollectors, onTransformedList
        )

        assertEquals(dispatches, asyncTransformer.pending.map { it.dispatch })
        verify(inverse = true) { onTransformedList(any()) }

        asyncTransformer.completeAll()

        verify(exactly = 1) { onTransformedList(dispatches) }
    }

    @Test
    fun transform_Applies_Transformations_Of_A_Dispatch_Serially_When_Transformer_Is_Async() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))
        val second = addTransformation(id = "second", transformerId = "transformer1")
        val firstResult = mockk<Dispatch>(relaxed = true)
        val secondResult = mockk<Dispatch>(relaxed = true)

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        val firstStep = asyncTransformer.pending.single()
        assertEquals(PendingTransformation(afterCollectors, mockDispatch), firstStep.transformation)

        asyncTransformer.complete(firstStep, firstResult)

        val secondStep = asyncTransformer.pending.single()
        assertEquals(PendingTransformation(second, firstResult), secondStep.transformation)
        verify(inverse = true) { onTransformed(any()) }

        asyncTransformer.complete(secondStep, secondResult)

        verify(exactly = 1) { onTransformed(secondResult) }
    }

    @Test
    fun transform_Does_Not_Apply_Next_Transformation_When_Disposed_Between_Transformations() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))
        addTransformation(id = "second", transformerId = "transformer1")

        val subscription = transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )
        val firstStep = asyncTransformer.pending.single()

        subscription.dispose()
        asyncTransformer.complete(firstStep, mockDispatch)

        assertEquals(listOf(firstStep), asyncTransformer.started)
        verify(exactly = 0) { onTransformed(any()) }
    }

    @Test
    fun transform_Disposes_Running_Transformation_When_Disposed() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))

        val subscription = transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )
        val step = asyncTransformer.pending.single()
        assertFalse(step.disposable.isDisposed)

        subscription.dispose()

        assertTrue(step.disposable.isDisposed)
    }

    @Test
    fun transform_Disposes_Running_Transformation_Of_Every_Dispatch_When_Batch_Is_Disposed() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))
        val dispatches = listOf<Dispatch>(
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)
        )

        val subscription = transformerCoordinator.transform(
            dispatches, DispatchScope.AfterCollectors, onTransformedList
        )
        val steps = asyncTransformer.pending.toList()
        assertEquals(3, steps.size)
        assertTrue(steps.none { it.disposable.isDisposed })

        subscription.dispose()

        assertTrue(steps.all { it.disposable.isDisposed })
    }

    /**
     * A synchronous transformer completes inside `applyTransformation`, so the next step has already
     * started and handed over its own Disposable before the synchronous step's (already disposed)
     * one is returned. The chain must keep every step rather than only the latest, or disposing it
     * would cancel nothing.
     */
    @Test
    fun transform_Disposes_Running_Async_Transformation_When_Disposed_After_A_Synchronous_Step() {
        val asyncTransformer = AsyncTransformer("async_transformer")
        registeredTransformers.onNext(listOf(mockTransformer1, asyncTransformer))
        val second = addTransformation(id = "second", transformerId = asyncTransformer.id)

        val subscription = transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )
        verify(exactly = 1) {
            mockTransformer1.applyTransformation(
                afterCollectors, mockDispatch, DispatchScope.AfterCollectors, any()
            )
        }
        val step = asyncTransformer.pending.single()
        assertEquals(PendingTransformation(second, mockDispatch), step.transformation)
        assertFalse(step.disposable.isDisposed)

        subscription.dispose()

        assertTrue(step.disposable.isDisposed)
        asyncTransformer.completeAll()
        verify(exactly = 0) { onTransformed(any()) }
    }

    @Test
    fun transform_Disposes_Running_Async_Transformations_Of_Every_Dispatch_When_Batch_Is_Disposed_After_A_Synchronous_Step() {
        val asyncTransformer = AsyncTransformer("async_transformer")
        registeredTransformers.onNext(listOf(mockTransformer1, asyncTransformer))
        val second = addTransformation(id = "second", transformerId = asyncTransformer.id)
        val dispatches = listOf<Dispatch>(mockk(relaxed = true), mockk(relaxed = true))

        val subscription = transformerCoordinator.transform(
            dispatches, DispatchScope.AfterCollectors, onTransformedList
        )
        dispatches.forEach { dispatch ->
            verify(exactly = 1) {
                mockTransformer1.applyTransformation(
                    afterCollectors, dispatch, DispatchScope.AfterCollectors, any()
                )
            }
        }
        val steps = asyncTransformer.pending.toList()
        assertEquals(
            dispatches.map { PendingTransformation(second, it) },
            steps.map { it.transformation }
        )
        assertTrue(steps.none { it.disposable.isDisposed })

        subscription.dispose()

        assertTrue(steps.all { it.disposable.isDisposed })
        asyncTransformer.completeAll()
        verify(exactly = 0) { onTransformedList(any()) }
    }

    @Test
    fun transform_Disposes_Completed_Transformation_After_Completion_And_Later_Dispose_Is_Harmless() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))

        val subscription = transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )
        val step = asyncTransformer.pending.single()
        asyncTransformer.complete(step, mockDispatch)
        assertTrue(subscription.isDisposed)

        subscription.dispose()

        assertTrue(step.disposable.isDisposed)
        assertEquals(1, asyncTransformer.started.size)
        verify(exactly = 1) { onTransformed(mockDispatch) }
    }

    @Test
    fun transform_Disposes_Completed_Transformations_After_Completion_And_Later_Batch_Dispose_Is_Harmless() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))
        val dispatches = listOf<Dispatch>(mockk(relaxed = true), mockk(relaxed = true))

        val subscription = transformerCoordinator.transform(
            dispatches, DispatchScope.AfterCollectors, onTransformedList
        )
        val steps = asyncTransformer.pending.toList()
        asyncTransformer.completeAll()
        assertTrue(subscription.isDisposed)

        subscription.dispose()

        assertTrue(steps.all { it.disposable.isDisposed })
        assertEquals(steps.size, asyncTransformer.started.size)
        verify(exactly = 1) { onTransformedList(dispatches) }
    }

    /**
     * [Transformer.applyTransformation] requires exactly one completion. A transformer that completes
     * twice must not re-run the rest of the chain: the duplicate is dropped.
     */
    @Test
    fun transform_Applies_Remaining_Transformations_Once_When_A_Transformer_Completes_Twice() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))
        val second = addTransformation(id = "second", transformerId = "transformer1")
        val firstResult = mockk<Dispatch>(relaxed = true)
        val secondResult = mockk<Dispatch>(relaxed = true)

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)
        val firstStep = asyncTransformer.pending.single()

        asyncTransformer.complete(firstStep, firstResult)
        asyncTransformer.completeAgain(firstStep, firstResult)

        val secondStep = asyncTransformer.pending.single()
        assertEquals(PendingTransformation(second, firstResult), secondStep.transformation)
        assertEquals(listOf(firstStep, secondStep), asyncTransformer.started)

        asyncTransformer.complete(secondStep, secondResult)

        assertEquals(listOf(firstStep, secondStep), asyncTransformer.started)
        verify(exactly = 1) { onTransformed(secondResult) }
    }

    @Test
    fun transform_Completes_Single_Dispatch_Once_When_A_Transformer_Completes_Twice() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)
        val step = asyncTransformer.pending.single()

        asyncTransformer.complete(step, mockDispatch)
        asyncTransformer.completeAgain(step, mockDispatch)

        verify(exactly = 1) { onTransformed(any()) }
    }

    @Test
    fun transform_Completes_Batch_Once_When_A_Transformer_Completes_Twice() {
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))
        val dispatches = listOf<Dispatch>(mockk(relaxed = true), mockk(relaxed = true))

        transformerCoordinator.transform(
            dispatches, DispatchScope.AfterCollectors, onTransformedList
        )
        val (stepA, stepB) = asyncTransformer.pending.toList()

        asyncTransformer.complete(stepA, stepA.dispatch)
        asyncTransformer.completeAgain(stepA, stepA.dispatch)
        verify(exactly = 0) { onTransformedList(any()) }

        asyncTransformer.complete(stepB, stepB.dispatch)

        verify(exactly = 1) { onTransformedList(any()) }
        verify(exactly = 1) { onTransformedList(dispatches) }
    }

    @Test
    fun transform_Logs_Warning_When_A_Transformer_Completes_Twice() {
        val logger = mockk<Logger>(relaxed = true)
        every { logger.shouldLog(LogLevel.WARN) } returns true
        val transformerCoordinator = TransformerCoordinatorImpl(
            registeredTransformers, transformationsSettings, Scheduler.SYNCHRONOUS, logger
        )
        val asyncTransformer = AsyncTransformer("transformer1")
        registeredTransformers.onNext(listOf(asyncTransformer))

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)
        val step = asyncTransformer.pending.single()

        asyncTransformer.complete(step, mockDispatch)
        verify(exactly = 0) { logger.warn(any(), any<String>()) }

        asyncTransformer.completeAgain(step, mockDispatch)

        verify(exactly = 1) {
            logger.warn(LogCategory.TRANSFORMATIONS, match<String> {
                it.contains("transformer1") && it.contains(afterCollectors.id)
            })
        }
    }

    @Test
    fun transform_Continues_Chain_On_Tealium_Scheduler_When_Transformer_Completes_On_Another_Thread() {
        val transformer = ThreadRecordingTransformer("transformer1")
        registeredTransformers.onNext(listOf(transformer))
        val second = addTransformation(id = "second", transformerId = "transformer1")
        val coordinator = tealiumSchedulerCoordinator()
        val tealiumThread = tealiumThread()
        val firstResult = mockk<Dispatch>(relaxed = true)
        val secondResult = mockk<Dispatch>(relaxed = true)
        val completions = Recorder<Completed<Dispatch?>>()

        tealiumScheduler.execute {
            coordinator.transform(mockDispatch, DispatchScope.AfterCollectors) {
                completions.record(Completed(it, Thread.currentThread()))
            }
        }
        val firstStep = transformer.steps.await()
        completeOnAnotherThread(firstStep, firstResult)

        val secondStep = transformer.steps.await()
        assertEquals(second, secondStep.transformation)
        assertSame(firstResult, secondStep.dispatch)
        assertSame(tealiumThread, secondStep.thread)

        tealiumScheduler.execute { secondStep.completion.onComplete(secondResult) }

        assertEquals(Completed<Dispatch?>(secondResult, tealiumThread), completions.await())
        tealiumScheduler.awaitQueuedTasks()
        assertTrue(completions.isEmpty())
    }

    @Test
    fun transform_Calls_Completion_On_Tealium_Scheduler_When_Single_Dispatch_Transformer_Completes_On_Another_Thread() {
        val transformer = ThreadRecordingTransformer("transformer1")
        registeredTransformers.onNext(listOf(transformer))
        val coordinator = tealiumSchedulerCoordinator()
        val tealiumThread = tealiumThread()
        val result = mockk<Dispatch>(relaxed = true)
        val completions = Recorder<Completed<Dispatch?>>()

        tealiumScheduler.execute {
            coordinator.transform(mockDispatch, DispatchScope.AfterCollectors) {
                completions.record(Completed(it, Thread.currentThread()))
            }
        }
        completeOnAnotherThread(transformer.steps.await(), result)

        assertEquals(Completed<Dispatch?>(result, tealiumThread), completions.await())
        tealiumScheduler.awaitQueuedTasks()
        assertTrue(completions.isEmpty())
    }

    @Test
    fun transform_Calls_Batch_Completion_On_Tealium_Scheduler_When_Transformers_Complete_On_Another_Thread() {
        val transformer = ThreadRecordingTransformer("transformer1")
        registeredTransformers.onNext(listOf(transformer))
        val second = addTransformation(id = "second", transformerId = "transformer1")
        val coordinator = tealiumSchedulerCoordinator()
        val tealiumThread = tealiumThread()
        val dispatches = listOf<Dispatch>(mockk(relaxed = true), mockk(relaxed = true))
        val completions = Recorder<Completed<List<Dispatch>>>()

        tealiumScheduler.execute {
            coordinator.transform(dispatches, DispatchScope.AfterCollectors) {
                completions.record(Completed(it, Thread.currentThread()))
            }
        }
        val firstSteps = listOf(transformer.steps.await(), transformer.steps.await())
        firstSteps.forEach { completeOnAnotherThread(it, it.dispatch) }
        val secondSteps = listOf(transformer.steps.await(), transformer.steps.await())
        secondSteps.forEach { step ->
            assertEquals(second, step.transformation)
            assertSame(tealiumThread, step.thread)
        }
        secondSteps.forEach { completeOnAnotherThread(it, it.dispatch) }

        assertEquals(Completed(dispatches, tealiumThread), completions.await())
        tealiumScheduler.awaitQueuedTasks()
        assertTrue(completions.isEmpty())
    }

    /**
     * The hop onto the Tealium scheduler is inline on its own thread: a chain of synchronous
     * transformers still completes before [TransformerCoordinatorImpl.transform] returns.
     */
    @Test
    fun transform_Applies_Next_Transformation_Inline_When_Transformer_Completes_Synchronously() {
        val applied = mutableListOf<TransformationSettings>()
        val transformer = TestTransformer("transformer1") { transformation, dispatch, _ ->
            applied.add(transformation)
            dispatch
        }
        registeredTransformers.onNext(listOf(transformer))
        val second = addTransformation(id = "second", transformerId = "transformer1")
        val coordinator = tealiumSchedulerCoordinator()
        val runs = Recorder<InlineRun>()

        tealiumScheduler.execute {
            var completed: Dispatch? = null
            val disposable = coordinator.transform(mockDispatch, DispatchScope.AfterCollectors) {
                completed = it
            }
            runs.record(InlineRun(applied.toList(), completed, disposable.isDisposed))
        }

        assertEquals(
            InlineRun(listOf(afterCollectors, second), mockDispatch, true),
            runs.await()
        )
    }

    /**
     * Nothing forces the two completions to overlap, so this checks that the second is ignored, not
     * that the guard is atomic.
     */
    @Test
    fun transform_Ignores_Second_Completion_When_Transformer_Completes_Twice_From_Different_Threads() {
        val transformer = ThreadRecordingTransformer("transformer1")
        registeredTransformers.onNext(listOf(transformer))
        val second = addTransformation(id = "second", transformerId = "transformer1")
        val coordinator = tealiumSchedulerCoordinator()
        val firstResult = mockk<Dispatch>(relaxed = true)
        val secondResult = mockk<Dispatch>(relaxed = true)
        val completions = Recorder<Dispatch?>()

        tealiumScheduler.execute {
            coordinator.transform(mockDispatch, DispatchScope.AfterCollectors) {
                completions.record(it)
            }
        }
        val firstStep = transformer.steps.await()

        val start = CountDownLatch(1)
        val other = Thread {
            start.await(5, TimeUnit.SECONDS)
            firstStep.completion.onComplete(firstResult)
        }.apply { start() }
        start.countDown()
        firstStep.completion.onComplete(firstResult)
        other.join(5_000)

        val secondStep = transformer.steps.await()
        assertEquals(second, secondStep.transformation)
        tealiumScheduler.awaitQueuedTasks()
        assertTrue(transformer.steps.isEmpty())

        tealiumScheduler.execute { secondStep.completion.onComplete(secondResult) }

        assertSame(secondResult, completions.await())
        tealiumScheduler.awaitQueuedTasks()
        assertTrue(completions.isEmpty())
    }

    private fun addTransformation(
        id: String = "after_collectors_with_conditions",
        transformerId: String = "transformer4",
        scope: TransformationScope = TransformationScope.AfterCollectors,
        conditions: Rule<Condition>? = null,
        order: Int? = null,
    ): TransformationSettings {
        val transformationSettings = if (order == null) {
            TransformationSettings(id, transformerId, scope, conditions = conditions)
        } else
            TransformationSettings(id, transformerId, scope, conditions = conditions, order = order)

        val withAddedTransformations = transformationsSettings.value + transformationSettings
        transformationsSettings.onNext(withAddedTransformations)

        return transformationSettings
    }

    private class TrackingSubscriptions<T>(
        val observable: Observable<T>,
        private val subscriptions: List<Disposable>
    ) {
        val subscriptionCount: Int
            get() = subscriptions.size
        val allDisposed: Boolean
            get() = subscriptions.all { it.isDisposed }
    }

    /**
     * Wraps [source] so that each subscription to the returned observable is recorded, allowing
     * tests to check whether it was later disposed.
     */
    private fun <T> trackingSubscriptions(source: Observable<T>): TrackingSubscriptions<T> {
        val subscriptions = mutableListOf<Disposable>()
        val observable = Observables.create<T> { observer ->
            source.subscribe(observer).also { subscriptions.add(it) }
        }
        return TrackingSubscriptions(observable, subscriptions)
    }

    private data class Completed<T>(val result: T, val thread: Thread)

    private data class InlineRun(
        val appliedBeforeReturn: List<TransformationSettings>,
        val completedBeforeReturn: Dispatch?,
        val disposedOnReturn: Boolean
    )

    private data class AppliedStep(
        val transformation: TransformationSettings,
        val dispatch: Dispatch,
        val thread: Thread,
        val completion: Callback<Dispatch?>
    )

    /**
     * A [Transformer] that holds every completion for the test to call, recording each step with
     * the thread it was applied on. Safe to use from any thread.
     */
    private class ThreadRecordingTransformer(
        override val id: String,
        override val version: String = "1.0"
    ) : Transformer {
        val steps = Recorder<AppliedStep>()

        override fun applyTransformation(
            transformation: TransformationSettings,
            dispatch: Dispatch,
            scope: DispatchScope,
            completion: Callback<Dispatch?>
        ): Disposable {
            steps.record(AppliedStep(transformation, dispatch, Thread.currentThread(), completion))
            return Disposables.subscription()
        }
    }

    private fun tealiumSchedulerCoordinator(): TransformerCoordinatorImpl =
        TransformerCoordinatorImpl(
            registeredTransformers, transformationsSettings, tealiumScheduler, SystemLogger
        )

    private fun tealiumThread(): Thread {
        val thread = Recorder<Thread>()
        tealiumScheduler.execute { thread.record(Thread.currentThread()) }
        return thread.await()
    }

    private fun completeOnAnotherThread(step: AppliedStep, result: Dispatch?) {
        val thread = Thread { step.completion.onComplete(result) }
        thread.start()
        thread.join(5_000)
        assertFalse("Completing thread did not finish", thread.isAlive)
    }
}

package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.DispatchScope
import com.tealium.core.api.Transformer
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class TransformerCoordinatorTests {

    @RelaxedMockK
    private lateinit var mockDispatch: Dispatch

    @RelaxedMockK
    private lateinit var onTransformed: (Dispatch?) -> Unit
    @RelaxedMockK
    private lateinit var onTransformedList: (List<Dispatch?>) -> Unit

    private lateinit var executorService: ScheduledExecutorService
    private lateinit var mockTransformer1: Transformer
    private lateinit var mockTransformer2: Transformer
    private lateinit var mockTransformer3: Transformer
    private lateinit var registeredTransformers: Set<Transformer>
    private lateinit var scopedTransformations: StateSubject<Set<ScopedTransformation>>
    private lateinit var transformerCoordinator: TransformerCoordinatorImpl

    private val defaultScopedTransformations: Set<ScopedTransformation> = setOf(
        ScopedTransformation(
            "after_collectors",
            "transformer1",
            setOf(TransformationScope.AfterCollectors)
        ),
        ScopedTransformation(
            "all_dispatchers",
            "transformer2",
            setOf(TransformationScope.AllDispatchers)
        ),
        ScopedTransformation(
            "dispatcher_1",
            "transformer3",
            setOf(TransformationScope.Dispatcher("dispatcher_1"))
        ),
        ScopedTransformation(
            "dispatcher_1_and_2",
            "transformer1",
            setOf(
                TransformationScope.Dispatcher("dispatcher_1"),
                TransformationScope.Dispatcher("dispatcher_2")
            )
        ),
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        executorService = Executors.newSingleThreadScheduledExecutor()

        mockTransformer1 = mockTransformer("transformer1")
        mockTransformer2 = mockTransformer("transformer2")
        mockTransformer3 = mockTransformer("transformer3")

        registeredTransformers = setOf(
            mockTransformer1, mockTransformer2, mockTransformer3
        )

        scopedTransformations = Observables.stateSubject(defaultScopedTransformations)

        transformerCoordinator =
            TransformerCoordinatorImpl(
                registeredTransformers,
                scopedTransformations,
                executorService
            )
    }

    @Test
    fun transform_AppliesTransformations_FromTransformers_ScopedToAfterCollectors() {
        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                "after_collectors",
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
        }
    }

    @Test
    fun transform_DoesNotApplyTransformations_FromTransformers_NotScopedToAfterCollectors() {
        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(inverse = true) {
            mockTransformer2.applyTransformation(
                any(),
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
            mockTransformer3.applyTransformation(
                any(),
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
        }
    }

    @Test
    fun transform_AppliesTransformations_FromTransformers_ScopedToAfterCollectors_IncludingNewlyAdded() {
        scopedTransformations.onNext(
            defaultScopedTransformations + setOf(
                ScopedTransformation(
                    "transformation5",
                    "transformer2",
                    setOf(TransformationScope.AfterCollectors)
                )
            )
        )

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                "after_collectors",
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
            mockTransformer2.applyTransformation(
                "transformation5",
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
        }
    }

    @Test
    fun transform_AppliesTransformations_FromTransformers_ScopedToAllDispatchers() {
        transformerCoordinator.transform(
            listOf(mockDispatch),
            DispatchScope.Dispatcher("dispatcher_1"),
            onTransformedList
        )

        verify(ordering = Ordering.ORDERED) {
            mockTransformer2.applyTransformation(
                "all_dispatchers",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
            mockTransformer3.applyTransformation(
                "dispatcher_1",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
            mockTransformer1.applyTransformation(
                "dispatcher_1_and_2",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
    }

    @Test
    fun transform_DoesNotApplyTransformations_FromTransformers_NotScopedToDispatcher() {
        transformerCoordinator.transform(
            listOf(mockDispatch),
            DispatchScope.Dispatcher("dispatcher_1"),
            onTransformedList
        )

        verify(inverse = true) {
            mockTransformer1.applyTransformation(
                "after_collectors",
                mockDispatch,
                any(),
                any()
            )
        }
    }

    @Test
    fun transform_PrefersFirstTransformerId_WhenSameIdUsed() {
        val mockTransformerDuplicate = mockTransformer("transformer1")
        val scopedTransformations = Observables.stateSubject(
            defaultScopedTransformations + setOf(
                ScopedTransformation(
                    "after_collectors",
                    "transformer1",
                    setOf(TransformationScope.AfterCollectors)
                )
            )
        )
        val transformerCoordinator = TransformerCoordinatorImpl(
            registeredTransformers + setOf(mockTransformerDuplicate),
            scopedTransformations,
            executorService
        )

        transformerCoordinator.transform(
            mockDispatch,
            DispatchScope.AfterCollectors,
            onTransformed
        )

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                "after_collectors",
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
        }
        verify(inverse = true) {
            mockTransformerDuplicate.applyTransformation(
                "after_collectors",
                mockDispatch,
                any(),
                any()
            )
        }
    }

    @Test
    fun transform_ReturnsNull_WhenTransformerReturnsNull() {
        val completionCapture = slot<(Dispatch?) -> Unit>()
        every {
            mockTransformer1.applyTransformation(
                any(),
                any(),
                any(),
                capture(completionCapture)
            )
        } answers {
            completionCapture.captured(null)
        }

        transformerCoordinator.transform(
            mockDispatch,
            DispatchScope.AfterCollectors,
            onTransformed
        )

//        assertNull(transformed)

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                "after_collectors",
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
            onTransformed(null)
        }
    }

    @Test
    fun transform_RemovesAllFromList_WhenTransformerReturnsNull() {
        val mockDispatch1 = mockk<Dispatch>(relaxed = true)
        val mockDispatch2 = mockk<Dispatch>(relaxed = true)
        val mockDispatch3 = mockk<Dispatch>(relaxed = true)
        val completionCapture = slot<(Dispatch?) -> Unit>()
        every {
            mockTransformer2.applyTransformation(
                any(),
                any(), // null for all dispatches
                any(),
                capture(completionCapture)
            )
        } answers {
            completionCapture.captured(null)
        }


        transformerCoordinator.transform(
            listOf(mockDispatch1, mockDispatch2, mockDispatch3),
            DispatchScope.Dispatcher("dispatcher_1"),
            onTransformedList
        )

        verify {
            onTransformedList(match { it.isEmpty() })
        }
    }

    @Test
    fun transform_RemovesFromList_WhenTransformerReturnsNull() {
        val mockDispatch1 = mockk<Dispatch>(relaxed = true)
        val mockDispatch2 = mockk<Dispatch>(relaxed = true)
        val mockDispatch3 = mockk<Dispatch>(relaxed = true)
        val completionCapture = slot<(Dispatch?) -> Unit>()
        every {
            mockTransformer2.applyTransformation(
                any(),
                mockDispatch2,
                any(),
                capture(completionCapture)
            )
        } answers {
            completionCapture.captured(null)
        }


        transformerCoordinator.transform(
            listOf(mockDispatch1, mockDispatch2, mockDispatch3),
            DispatchScope.Dispatcher("dispatcher_1"),
            onTransformedList
        )

        verify {
            onTransformedList(match {
                it.contains(mockDispatch1)
                        && !it.contains(mockDispatch2) // dropped
                        && it.contains(mockDispatch3)
            })
        }
    }

    @Test
    fun transform_StopsTransforming_AfterFirstTransformerReturnsNull() {
        val completionCapture = slot<(Dispatch?) -> Unit>()
        every {
            mockTransformer2.applyTransformation(
                any(),
                mockDispatch,
                any(),
                capture(completionCapture)
            )
        } answers {
            completionCapture.captured(null)
        }

        transformerCoordinator.transform(
            listOf(mockDispatch),
            DispatchScope.Dispatcher("dispatcher_1"),
            onTransformedList
        )

        verify {
            mockTransformer2.applyTransformation(
                "all_dispatchers",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
        verify(inverse = true) {
            mockTransformer3.applyTransformation(
                "dispatcher_1",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
            mockTransformer1.applyTransformation(
                "dispatcher_1_and_2",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
    }

    /**
     * Returns a Mockk of a [Transformer] with the given [name]. The returned dispatcher's behaviour
     * is to capture and return dispatch sent to it unedited.
     */
    private fun mockTransformer(name: String): Transformer {
        return mockk<Transformer>(relaxed = true).also {
            val dispatchSlot = slot<Dispatch>()
            val completionSlot = slot<(Dispatch?) -> Unit>()

            every { it.id } returns name
            every {
                it.applyTransformation(
                    any(),
                    capture(dispatchSlot),
                    any(),
                    capture(completionSlot)
                )
            } answers {
                completionSlot.captured(dispatchSlot.captured)
            }
        }
    }
}
package com.tealium.core.internal.dispatch

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.ScopedTransformation
import com.tealium.core.api.transform.TransformationScope
import com.tealium.core.api.transform.Transformer
import com.tealium.tests.common.SynchronousScheduler
import com.tealium.tests.common.TestTransformer
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class TransformerCoordinatorTests {

    @RelaxedMockK
    private lateinit var mockDispatch: Dispatch

    @RelaxedMockK
    private lateinit var onTransformed: (Dispatch?) -> Unit

    @RelaxedMockK
    private lateinit var onTransformedList: (List<Dispatch?>) -> Unit

    private lateinit var mockTransformer1: Transformer
    private lateinit var mockTransformer2: Transformer
    private lateinit var mockTransformer3: Transformer
    private lateinit var registeredTransformers: StateSubject<List<Transformer>>
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

        mockTransformer1 = TestTransformer.mock("transformer1")
        mockTransformer2 = TestTransformer.mock("transformer2")
        mockTransformer3 = TestTransformer.mock("transformer3")

        registeredTransformers = Observables.stateSubject(
            listOf(mockTransformer1, mockTransformer2, mockTransformer3)
        )

        scopedTransformations = Observables.stateSubject(defaultScopedTransformations)

        transformerCoordinator =
            TransformerCoordinatorImpl(
                registeredTransformers,
                scopedTransformations,
                SynchronousScheduler()
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
        val mockTransformerDuplicate = TestTransformer.mock("transformer1")
        scopedTransformations.onNext(
            defaultScopedTransformations + setOf(
                ScopedTransformation(
                    "after_collectors",
                    "transformer1",
                    setOf(TransformationScope.AfterCollectors)
                )
            )
        )
        registeredTransformers.onNext(registeredTransformers.value + mockTransformerDuplicate)

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
        mockTransformer1 = TestTransformer.mock(mockTransformer1.id) { _, _, _ -> null }
        registeredTransformers.onNext(listOf(mockTransformer1))

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
            onTransformed(null)
        }
    }

    @Test
    fun transform_RemovesAllFromList_WhenTransformerReturnsNull() {
        val mockDispatch1 = mockk<Dispatch>(relaxed = true)
        val mockDispatch2 = mockk<Dispatch>(relaxed = true)
        val mockDispatch3 = mockk<Dispatch>(relaxed = true)
        mockTransformer2 = TestTransformer.mock(mockTransformer2.id) { _, _, _ -> null }
        registeredTransformers.onNext(listOf(mockTransformer1, mockTransformer2, mockTransformer3))

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
        mockTransformer2 = TestTransformer.mock(mockTransformer2.id) { _, dispatch, _ ->
            if (dispatch == mockDispatch2) {
                null
            } else dispatch
        }
        registeredTransformers.onNext(listOf(mockTransformer1, mockTransformer2, mockTransformer3))


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
        mockTransformer2 = TestTransformer.mock(mockTransformer2.id) { _, _, _ -> null }
        registeredTransformers.onNext(listOf(mockTransformer1, mockTransformer2, mockTransformer3))

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

    @Test
    fun transform_Only_Uses_Observed_Transformers() {
        registeredTransformers.onNext(listOf(mockTransformer3))

        transformerCoordinator.transform(
            listOf(mockDispatch),
            DispatchScope.Dispatcher("dispatcher_1"),
            onTransformedList
        )

        verify {
            mockTransformer3.applyTransformation(
                "dispatcher_1",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
        verify(inverse = true) {
            mockTransformer1.applyTransformation(
                "dispatcher_1_and_2",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
            mockTransformer2.applyTransformation(
                "all_dispatchers",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
    }

    @Test
    fun transform_Still_Call_Other_Transformer_If_Previous_One_Is_Missing() {
        registeredTransformers.onNext(listOf(mockTransformer1))

        transformerCoordinator.transform(
            listOf(mockDispatch),
            DispatchScope.Dispatcher("dispatcher_1"),
            onTransformedList
        )

        verify {
            mockTransformer1.applyTransformation(
                "dispatcher_1_and_2",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
        verify(inverse = true) {
            mockTransformer2.applyTransformation(
                "all_dispatchers",
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
    }
}
package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.DispatchScope
import com.tealium.core.api.Transformer
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransformerCoordinatorTests {

    @RelaxedMockK
    private lateinit var mockDispatch: Dispatch

    private lateinit var mockTransformer1: Transformer
    private lateinit var mockTransformer2: Transformer
    private lateinit var mockTransformer3: Transformer
    private lateinit var registeredTransformers: Set<Transformer>
    private lateinit var scopedTransformations: MutableStateFlow<Set<ScopedTransformation>>
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

        mockTransformer1 = mockTransformer("transformer1")
        mockTransformer2 = mockTransformer("transformer2")
        mockTransformer3 = mockTransformer("transformer3")

        registeredTransformers = setOf(
            mockTransformer1, mockTransformer2, mockTransformer3
        )

        scopedTransformations = MutableStateFlow(defaultScopedTransformations)

        transformerCoordinator =
            TransformerCoordinatorImpl(registeredTransformers, scopedTransformations)
    }

    @Test
    fun transform_AppliesTransformations_FromTransformers_ScopedToAfterCollectors() =
        runTest {
            transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors)

            coVerify(ordering = Ordering.ORDERED) {
                mockTransformer1.applyTransformation(
                    "after_collectors",
                    mockDispatch,
                    DispatchScope.AfterCollectors
                )
            }
        }

    @Test
    fun transform_DoesNotApplyTransformations_FromTransformers_NotScopedToAfterCollectors() =
        runTest {
            transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors)

            coVerify(inverse = true) {
                mockTransformer2.applyTransformation(
                    any(),
                    mockDispatch,
                    DispatchScope.AfterCollectors
                )
                mockTransformer3.applyTransformation(
                    any(),
                    mockDispatch,
                    DispatchScope.AfterCollectors
                )
            }
        }

    @Test
    fun transform_AppliesTransformations_FromTransformers_ScopedToAfterCollectors_IncludingNewlyAdded() =
        runTest {
            scopedTransformations.emit(
                defaultScopedTransformations + setOf(
                    ScopedTransformation(
                        "transformation5",
                        "transformer2",
                        setOf(TransformationScope.AfterCollectors)
                    )
                )
            )

            transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors)

            coVerify(ordering = Ordering.ORDERED) {
                mockTransformer1.applyTransformation(
                    "after_collectors",
                    mockDispatch,
                    DispatchScope.AfterCollectors
                )
                mockTransformer2.applyTransformation(
                    "transformation5",
                    mockDispatch,
                    DispatchScope.AfterCollectors
                )
            }
        }

    @Test
    fun transform_AppliesTransformations_FromTransformers_ScopedToAllDispatchers() =
        runTest {
            transformerCoordinator.transform(
                listOf(mockDispatch),
                DispatchScope.Dispatcher("dispatcher_1")
            )

            coVerify(ordering = Ordering.ORDERED) {
                mockTransformer2.applyTransformation(
                    "all_dispatchers",
                    mockDispatch,
                    DispatchScope.Dispatcher("dispatcher_1")
                )
                mockTransformer3.applyTransformation(
                    "dispatcher_1",
                    mockDispatch,
                    DispatchScope.Dispatcher("dispatcher_1")
                )
                mockTransformer1.applyTransformation(
                    "dispatcher_1_and_2",
                    mockDispatch,
                    DispatchScope.Dispatcher("dispatcher_1")
                )
            }
        }

    @Test
    fun transform_DoesNotApplyTransformations_FromTransformers_NotScopedToDispatcher() =
        runTest {
            transformerCoordinator.transform(
                listOf(mockDispatch),
                DispatchScope.Dispatcher("dispatcher_1")
            )

            coVerify(inverse = true) {
                mockTransformer1.applyTransformation(
                    "after_collectors",
                    mockDispatch,
                    any()
                )
            }
        }

    @Test
    fun transform_PrefersFirstTransformerId_WhenSameIdUsed() =
        runTest {
            val mockTransformerDuplicate = mockTransformer("transformer1")
            val scopedTransformations = MutableStateFlow(
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
                scopedTransformations
            )

            transformerCoordinator.transform(
                mockDispatch,
                DispatchScope.AfterCollectors
            )

            coVerify(ordering = Ordering.ORDERED) {
                mockTransformer1.applyTransformation(
                    "after_collectors",
                    mockDispatch,
                    DispatchScope.AfterCollectors
                )
            }
            coVerify(inverse = true) {
                mockTransformerDuplicate.applyTransformation(
                    "after_collectors",
                    mockDispatch,
                    any()
                )
            }
        }

    @Test
    fun transform_ReturnsNull_WhenTransformerReturnsNull() =
        runTest {
            coEvery {
                mockTransformer1.applyTransformation(
                    any(),
                    any(),
                    any()
                )
            } returns null

            val transformed = transformerCoordinator.transform(
                mockDispatch,
                DispatchScope.AfterCollectors
            )

            assertNull(transformed)

            coVerify(ordering = Ordering.ORDERED) {
                mockTransformer1.applyTransformation(
                    "after_collectors",
                    mockDispatch,
                    DispatchScope.AfterCollectors
                )
            }
        }

    @Test
    fun transform_RemovesAllFromList_WhenTransformerReturnsNull() =
        runTest {
            val mockDispatch1 = mockk<Dispatch>(relaxed = true)
            val mockDispatch2 = mockk<Dispatch>(relaxed = true)
            val mockDispatch3 = mockk<Dispatch>(relaxed = true)
            coEvery {
                mockTransformer2.applyTransformation(
                    any(),
                    any(), // null for all dispatches
                    any()
                )
            } returns null


            val transformed = transformerCoordinator.transform(
                listOf(mockDispatch1, mockDispatch2, mockDispatch3),
                DispatchScope.Dispatcher("dispatcher_1")
            )

            assertTrue(transformed.isEmpty())
        }

    @Test
    fun transform_RemovesFromList_WhenTransformerReturnsNull() =
        runTest {
            val mockDispatch1 = mockk<Dispatch>(relaxed = true)
            val mockDispatch2 = mockk<Dispatch>(relaxed = true)
            val mockDispatch3 = mockk<Dispatch>(relaxed = true)
            coEvery {
                mockTransformer2.applyTransformation(
                    any(),
                    mockDispatch2,
                    any()
                )
            } returns null


            val transformed = transformerCoordinator.transform(
                listOf(mockDispatch1, mockDispatch2, mockDispatch3),
                DispatchScope.Dispatcher("dispatcher_1")
            )

            assertTrue(transformed.contains(mockDispatch1))
            assertFalse(transformed.contains(mockDispatch2))
            assertTrue(transformed.contains(mockDispatch3))
        }

    @Test
    fun transform_StopsTransforming_AfterFirstTransformerReturnsNull() =
        runTest {
            coEvery {
                mockTransformer2.applyTransformation(
                    any(),
                    mockDispatch,
                    any()
                )
            } returns null


            transformerCoordinator.transform(
                listOf(mockDispatch),
                DispatchScope.Dispatcher("dispatcher_1")
            )

            coVerify {
                mockTransformer2.applyTransformation(
                    "all_dispatchers",
                    mockDispatch,
                    DispatchScope.Dispatcher("dispatcher_1")
                )
            }
            coVerify(inverse = true) {
                mockTransformer3.applyTransformation(
                    "dispatcher_1",
                    mockDispatch,
                    DispatchScope.Dispatcher("dispatcher_1")
                )
                mockTransformer1.applyTransformation(
                    "dispatcher_1_and_2",
                    mockDispatch,
                    DispatchScope.Dispatcher("dispatcher_1")
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

            every { it.id } returns name
            coEvery {
                it.applyTransformation(
                    any(),
                    capture(dispatchSlot),
                    any()
                )
            } answers { dispatchSlot.captured }
        }
    }
}
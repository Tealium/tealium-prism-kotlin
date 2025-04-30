package com.tealium.core.internal.dispatch

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.rules.Condition
import com.tealium.core.api.rules.Rule
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.TransformationScope
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.api.transform.Transformer
import com.tealium.tests.common.SynchronousScheduler
import com.tealium.tests.common.TestTransformer
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    private lateinit var transformationsSettings: StateSubject<Set<TransformationSettings>>
    private lateinit var mappings: StateSubject<Map<String, TransformationSettings>>
    private lateinit var transformerCoordinator: TransformerCoordinatorImpl

    private val afterCollectors = TransformationSettings(
        "after_collectors",
        "transformer1",
        setOf(TransformationScope.AfterCollectors)
    )
    private val allDispatchers = TransformationSettings(
        "all_dispatchers",
        "transformer2",
        setOf(TransformationScope.AllDispatchers)
    )
    private val dispatcher1 = TransformationSettings(
        "dispatcher_1",
        "transformer3",
        setOf(TransformationScope.Dispatcher("dispatcher_1"))
    )
    private val dispatcher1And2 = TransformationSettings(
        "dispatcher_1_and_2",
        "transformer1",
        setOf(
            TransformationScope.Dispatcher("dispatcher_1"),
            TransformationScope.Dispatcher("dispatcher_2")
        )
    )
    private val defaultTransformationSettings: Set<TransformationSettings> = setOf(
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

        registeredTransformers = Observables.stateSubject(
            listOf(mockTransformer1, mockTransformer2, mockTransformer3)
        )

        transformationsSettings = Observables.stateSubject(defaultTransformationSettings)
        mappings = Observables.stateSubject(emptyMap())

        transformerCoordinator =
            TransformerCoordinatorImpl(
                registeredTransformers,
                transformationsSettings,
                mappings,
                SynchronousScheduler()
            )
    }

    @Test
    fun transform_AppliesTransformations_FromTransformers_ScopedToAfterCollectors() {
        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                afterCollectors,
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
        val transformation5 = TransformationSettings(
            "transformation5",
            "transformer2",
            setOf(TransformationScope.AfterCollectors)
        )
        transformationsSettings.onNext(
            defaultTransformationSettings + setOf(transformation5)
        )

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                afterCollectors,
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
            mockTransformer2.applyTransformation(
                transformation5,
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
                allDispatchers,
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
            mockTransformer3.applyTransformation(
                dispatcher1,
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
            mockTransformer1.applyTransformation(
                dispatcher1And2,
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
                afterCollectors,
                mockDispatch,
                any(),
                any()
            )
        }
    }

    @Test
    fun transform_Applies_Mapping_Transformation_When_DispatchScope_Is_Dispatcher_Specific() {
        val mappingTransformation = TransformationSettings(
            "mappings",
            mockTransformer3.id,
            setOf(TransformationScope.Dispatcher("dispatcher_3"))
        )
        val scope = DispatchScope.Dispatcher("dispatcher_3")
        mappings.onNext(mapOf("dispatcher_3" to mappingTransformation))

        transformerCoordinator.transform(listOf(mockDispatch), scope, mockk(relaxed = true))

        verify {
            mockTransformer3.applyTransformation(
                mappingTransformation, mockDispatch, scope, any()
            )
        }
    }

    @Test
    fun transform_Does_Not_Apply_Mapping_Transformation_When_DispatchScope_Is_After_Collectors() {
        val mappingTransformation = TransformationSettings(
            "mappings",
            mockTransformer3.id,
            setOf(TransformationScope.Dispatcher("dispatcher_3"))
        )
        val scope = DispatchScope.AfterCollectors
        mappings.onNext(mapOf("dispatcher_3" to mappingTransformation))

        transformerCoordinator.transform(listOf(mockDispatch), scope, mockk(relaxed = true))

        verify(inverse = true) {
            mockTransformer3.applyTransformation(
                mappingTransformation, mockDispatch, scope, any()
            )
        }
    }

    @Test
    fun transform_Applies_Mapping_Transformation_After_All_Other_Transformations() {
        val mappingTransformation = TransformationSettings(
            "mappings",
            mockTransformer3.id,
            setOf(TransformationScope.Dispatcher("dispatcher_1"))
        )
        val scope = DispatchScope.Dispatcher("dispatcher_1")
        mappings.onNext(mapOf("dispatcher_1" to mappingTransformation))

        transformerCoordinator.transform(listOf(mockDispatch), scope, mockk(relaxed = true))

        verifyOrder {
            mockTransformer3.applyTransformation(
                dispatcher1, mockDispatch, scope, any()
            )
            mockTransformer3.applyTransformation(
                mappingTransformation, mockDispatch, scope, any()
            )
        }
    }

    @Test
    fun transform_Applies_Transformation_After_Collectors_When_Conditions_Match_Dispatch() {
        val settings = TransformationSettings(
            "conditioned",
            mockTransformer3.id,
            setOf(TransformationScope.AfterCollectors),
            conditions = Rule.just(Condition.isDefined(null, "is_defined"))
        )
        transformationsSettings.onNext(transformationsSettings.value + settings)

        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put("is_defined", true)
        })
        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        verify {
            mockTransformer3.applyTransformation(
                settings, dispatch, DispatchScope.AfterCollectors, any()
            )
        }
    }

    @Test
    fun transform_Applies_Transformation_For_Dispatchers_When_Conditions_Match_Dispatch() {
        val settings = TransformationSettings(
            "conditioned",
            mockTransformer3.id,
            setOf(TransformationScope.AllDispatchers),
            conditions = Rule.just(Condition.isDefined(null, "is_defined"))
        )
        transformationsSettings.onNext(transformationsSettings.value + settings)

        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put("is_defined", true)
        })
        transformerCoordinator.transform(
            listOf(dispatch),
            DispatchScope.Dispatcher("any"),
            mockk(relaxed = true)
        )

        verify {
            mockTransformer3.applyTransformation(
                settings, dispatch, DispatchScope.Dispatcher("any"), any()
            )
        }
    }

    @Test
    fun transform_Does_Not_Apply_Transformation_After_Collectors_When_Conditions_Do_Not_Match_Dispatch() {
        val settings = TransformationSettings(
            "conditioned",
            mockTransformer3.id,
            setOf(TransformationScope.AllDispatchers),
            conditions = Rule.just(Condition.isDefined(null, "is_defined"))
        )
        transformationsSettings.onNext(transformationsSettings.value + settings)

        val dispatch = Dispatch.create("nothing_defined")
        transformerCoordinator.transform(
            dispatch,
            DispatchScope.AfterCollectors,
            onTransformed
        )

        verify(inverse = true)  {
            mockTransformer3.applyTransformation(
                settings, dispatch, DispatchScope.AfterCollectors, any()
            )
        }
    }

    @Test
    fun transform_Does_Not_Apply_Transformation_For_Dispatchers_When_Conditions_Do_Not_Match_Dispatch() {
        val settings = TransformationSettings(
            "conditioned",
            mockTransformer3.id,
            setOf(TransformationScope.AllDispatchers),
            conditions = Rule.just(Condition.isDefined(null, "is_defined"))
        )
        transformationsSettings.onNext(transformationsSettings.value + settings)

        val dispatch = Dispatch.create("nothing_defined")
        transformerCoordinator.transform(
            listOf(dispatch),
            DispatchScope.Dispatcher("any"),
            mockk(relaxed = true)
        )

        verify(inverse = true) {
            mockTransformer3.applyTransformation(
                settings, dispatch, DispatchScope.Dispatcher("any"), any()
            )
        }
    }

    @Test
    fun transform_PrefersFirstTransformerId_WhenSameIdUsed() {
        val mockTransformerDuplicate = TestTransformer.mock("transformer1")
        transformationsSettings.onNext(
            defaultTransformationSettings + setOf(
                TransformationSettings(
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
                afterCollectors,
                mockDispatch,
                DispatchScope.AfterCollectors,
                any()
            )
        }
        verify(inverse = true) {
            mockTransformerDuplicate.applyTransformation(
                afterCollectors,
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
                afterCollectors,
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
                allDispatchers,
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
        verify(inverse = true) {
            mockTransformer3.applyTransformation(
                dispatcher1,
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
            mockTransformer1.applyTransformation(
                dispatcher1And2,
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
                dispatcher1,
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
        verify(inverse = true) {
            mockTransformer1.applyTransformation(
                dispatcher1And2,
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
            mockTransformer2.applyTransformation(
                allDispatchers,
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
                dispatcher1And2,
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
        verify(inverse = true) {
            mockTransformer2.applyTransformation(
                allDispatchers,
                mockDispatch,
                DispatchScope.Dispatcher("dispatcher_1"),
                any()
            )
        }
    }

    @Test
    fun registerTransformation_Adds_Transformation_When_Not_Already_Registered() {
        val newTransformation =
            TransformationSettings("new", "transformer", setOf(TransformationScope.AllDispatchers))
        transformerCoordinator.registerTransformation(newTransformation)

        assertTrue(transformerCoordinator.allTransformations.contains(newTransformation))
    }

    @Test
    fun registerTransformation_Does_Not_Add_Transformation_When_Another_Already_Registered_With_Same_Ids() {
        val newTransformation =
            TransformationSettings("new", "transformer", setOf(TransformationScope.AllDispatchers))
        val duplicated =
            newTransformation.copy(configuration = DataObject.create { put("different", "config") })
        transformerCoordinator.registerTransformation(newTransformation)
        transformerCoordinator.registerTransformation(duplicated)

        assertTrue(transformerCoordinator.allTransformations.contains(newTransformation))
        assertFalse(transformerCoordinator.allTransformations.contains(duplicated))
    }

    @Test
    fun unregisterTransformation_Removes_Transformation_When_Already_Registered() {
        val newTransformation =
            TransformationSettings("new", "transformer", setOf(TransformationScope.AllDispatchers))
        transformerCoordinator.registerTransformation(newTransformation)
        transformerCoordinator.unregisterTransformation(newTransformation)

        assertFalse(transformerCoordinator.allTransformations.contains(newTransformation))
    }

    @Test
    fun unregisterTransformation_Removes_Transformation_When_Another_Already_Registered_With_Same_Ids() {
        val newTransformation =
            TransformationSettings("new", "transformer", setOf(TransformationScope.AllDispatchers))
        val duplicated =
            newTransformation.copy(configuration = DataObject.create { put("different", "config") })
        transformerCoordinator.registerTransformation(newTransformation)
        transformerCoordinator.unregisterTransformation(duplicated)

        assertFalse(transformerCoordinator.allTransformations.contains(newTransformation))
    }
}
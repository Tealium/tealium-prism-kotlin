package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Condition.Companion.isDefined
import com.tealium.prism.core.api.rules.Condition.Companion.isGreaterThan
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestTransformer
import io.mockk.MockKAnnotations
import io.mockk.Ordering
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
    private lateinit var mockTransformer4: Transformer
    private lateinit var registeredTransformers: StateSubject<List<Transformer>>
    private lateinit var transformationsSettings: StateSubject<List<TransformationSettings>>
    private lateinit var transformerCoordinator: TransformerCoordinatorImpl

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

        registeredTransformers = Observables.stateSubject(
            listOf(mockTransformer1, mockTransformer2, mockTransformer3, mockTransformer4)
        )

        transformationsSettings = Observables.stateSubject(defaultTransformationSettings)

        transformerCoordinator = TransformerCoordinatorImpl(
            registeredTransformers, transformationsSettings, Scheduler.SYNCHRONOUS, SystemLogger
        )
    }

    @Test
    fun transform_With_Empty_List_Completes_With_Empty_List() {
        transformerCoordinator.transform(
            emptyList(),
            DispatchScope.AfterCollectors,
            onTransformedList
        )

        verify { onTransformedList.invoke(emptyList()) }
    }

    @Test
    fun transform_Applies_Transformations_From_Transformers_Scoped_To_AfterCollectors() {
        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                afterCollectors, mockDispatch, DispatchScope.AfterCollectors, any()
            )
        }
    }

    @Test
    fun transform_Does_Not_Apply_Transformations_From_Transformers_Not_Scoped_To_AfterCollectors() {
        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(inverse = true) {
            mockTransformer2.applyTransformation(
                any(), mockDispatch, DispatchScope.AfterCollectors, any()
            )
            mockTransformer3.applyTransformation(
                any(), mockDispatch, DispatchScope.AfterCollectors, any()
            )
        }
    }

    @Test
    fun transform_Applies_Transformations_From_Transformers_Scoped_To_AfterCollectors_Including_Newly_Added() {
        val transformation5 = TransformationSettings(
            "transformation5", "transformer2", TransformationScope.AfterCollectors
        )
        transformationsSettings.onNext(
            defaultTransformationSettings + listOf(transformation5)
        )

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                afterCollectors, mockDispatch, DispatchScope.AfterCollectors, any()
            )
            mockTransformer2.applyTransformation(
                transformation5, mockDispatch, DispatchScope.AfterCollectors, any()
            )
        }
    }

    @Test
    fun transform_Applies_Transformations_From_Transformers_Scoped_To_AllDispatchers() {
        transformerCoordinator.transform(
            listOf(mockDispatch), DispatchScope.Dispatcher("dispatcher_1"), onTransformedList
        )

        verify(ordering = Ordering.ORDERED) {
            mockTransformer2.applyTransformation(
                allDispatchers, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
            mockTransformer3.applyTransformation(
                dispatcher1, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
            mockTransformer1.applyTransformation(
                dispatcher1And2, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
        }
    }

    @Test
    fun transform_Does_Not_Apply_Transformations_From_Transformers_Not_Scoped_To_Dispatcher() {
        transformerCoordinator.transform(
            listOf(mockDispatch), DispatchScope.Dispatcher("dispatcher_1"), onTransformedList
        )

        verify(inverse = true) {
            mockTransformer1.applyTransformation(
                afterCollectors, mockDispatch, any(), any()
            )
        }
    }

    @Test
    fun transform_Applies_Transformation_After_Collectors_When_Conditions_Match_Dispatch() {
        val settings = TransformationSettings(
            "conditioned",
            mockTransformer3.id,
            TransformationScope.AfterCollectors,
            conditions = Rule.just(isDefined("is_defined"))
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
            TransformationScope.AllDispatchers,
            conditions = Rule.just(isDefined("is_defined"))
        )
        transformationsSettings.onNext(transformationsSettings.value + settings)

        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put("is_defined", true)
        })
        transformerCoordinator.transform(
            listOf(dispatch), DispatchScope.Dispatcher("any"), mockk(relaxed = true)
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
            TransformationScope.AllDispatchers,
            conditions = Rule.just(isDefined("is_defined"))
        )
        transformationsSettings.onNext(transformationsSettings.value + settings)

        val dispatch = Dispatch.create("nothing_defined")
        transformerCoordinator.transform(
            dispatch, DispatchScope.AfterCollectors, onTransformed
        )

        verify(inverse = true) {
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
            TransformationScope.AllDispatchers,
            conditions = Rule.just(isDefined("is_defined"))
        )
        transformationsSettings.onNext(transformationsSettings.value + settings)

        val dispatch = Dispatch.create("nothing_defined")
        transformerCoordinator.transform(
            listOf(dispatch), DispatchScope.Dispatcher("any"), mockk(relaxed = true)
        )

        verify(inverse = true) {
            mockTransformer3.applyTransformation(
                settings, dispatch, DispatchScope.Dispatcher("any"), any()
            )
        }
    }

    @Test
    fun transform_Prefers_First_Transformer_Id_When_Same_Id_Used() {
        val mockTransformerDuplicate = TestTransformer.mock("transformer1")
        transformationsSettings.onNext(
            defaultTransformationSettings + listOf(
                TransformationSettings(
                    "after_collectors", "transformer1", TransformationScope.AfterCollectors
                )
            )
        )
        registeredTransformers.onNext(registeredTransformers.value + mockTransformerDuplicate)

        transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                afterCollectors, mockDispatch, DispatchScope.AfterCollectors, any()
            )
        }
        verify(inverse = true) {
            mockTransformerDuplicate.applyTransformation(
                afterCollectors, mockDispatch, any(), any()
            )
        }
    }

    @Test
    fun transform_Returns_Null_When_Transformer_Returns_Null() {
        mockTransformer1 = TestTransformer.mock(mockTransformer1.id) { _, _, _ -> null }
        registeredTransformers.onNext(listOf(mockTransformer1))

        transformerCoordinator.transform(
            mockDispatch, DispatchScope.AfterCollectors, onTransformed
        )

        verify(ordering = Ordering.ORDERED) {
            mockTransformer1.applyTransformation(
                afterCollectors, mockDispatch, DispatchScope.AfterCollectors, any()
            )
            onTransformed(null)
        }
    }

    @Test
    fun transform_Removes_All_From_List_When_Transformer_Returns_Null() {
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
    fun transform_Removes_From_List_When_Transformer_Returns_Null() {
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
                it.contains(mockDispatch1) && !it.contains(mockDispatch2) // dropped
                        && it.contains(mockDispatch3)
            })
        }
    }

    @Test
    fun transform_Stops_Transforming_After_First_Transformer_Returns_Null() {
        mockTransformer2 = TestTransformer.mock(mockTransformer2.id) { _, _, _ -> null }
        registeredTransformers.onNext(listOf(mockTransformer1, mockTransformer2, mockTransformer3))

        transformerCoordinator.transform(
            listOf(mockDispatch), DispatchScope.Dispatcher("dispatcher_1"), onTransformedList
        )

        verify {
            mockTransformer2.applyTransformation(
                allDispatchers, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
        }
        verify(inverse = true) {
            mockTransformer3.applyTransformation(
                dispatcher1, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
            mockTransformer1.applyTransformation(
                dispatcher1And2, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
        }
    }

    @Test
    fun transform_Only_Uses_Observed_Transformers() {
        registeredTransformers.onNext(listOf(mockTransformer3))

        transformerCoordinator.transform(
            listOf(mockDispatch), DispatchScope.Dispatcher("dispatcher_1"), onTransformedList
        )

        verify {
            mockTransformer3.applyTransformation(
                dispatcher1, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
        }
        verify(inverse = true) {
            mockTransformer1.applyTransformation(
                dispatcher1And2, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
            mockTransformer2.applyTransformation(
                allDispatchers, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
        }
    }

    @Test
    fun transform_Still_Call_Other_Transformer_If_Previous_One_Is_Missing() {
        registeredTransformers.onNext(listOf(mockTransformer1))

        transformerCoordinator.transform(
            listOf(mockDispatch), DispatchScope.Dispatcher("dispatcher_1"), onTransformedList
        )

        verify {
            mockTransformer1.applyTransformation(
                dispatcher1And2, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
        }
        verify(inverse = true) {
            mockTransformer2.applyTransformation(
                allDispatchers, mockDispatch, DispatchScope.Dispatcher("dispatcher_1"), any()
            )
        }
    }

    @Test
    fun registerTransformation_Adds_Transformation_When_Not_Already_Registered() {
        val newTransformation =
            TransformationSettings("new", "transformer", TransformationScope.AllDispatchers)
        transformerCoordinator.registerTransformation(newTransformation)

        assertTrue(transformerCoordinator.sortedTransformations.contains(newTransformation))
    }

    @Test
    fun registerTransformation_Does_Not_Add_Transformation_When_Another_Already_Registered_With_Same_Ids() {
        val newTransformation =
            TransformationSettings("new", "transformer", TransformationScope.AllDispatchers)
        val duplicated =
            newTransformation.copy(configuration = DataObject.create { put("different", "config") })
        transformerCoordinator.registerTransformation(newTransformation)
        transformerCoordinator.registerTransformation(duplicated)

        assertTrue(transformerCoordinator.sortedTransformations.contains(newTransformation))
        assertFalse(transformerCoordinator.sortedTransformations.contains(duplicated))
    }

    @Test
    fun unregisterTransformation_Removes_Transformation_When_Already_Registered() {
        val newTransformation =
            TransformationSettings("new", "transformer", TransformationScope.AllDispatchers)
        transformerCoordinator.registerTransformation(newTransformation)
        transformerCoordinator.unregisterTransformation(newTransformation)

        assertFalse(transformerCoordinator.sortedTransformations.contains(newTransformation))
    }

    @Test
    fun unregisterTransformation_Removes_Transformation_When_Another_Already_Registered_With_Same_Ids() {
        val newTransformation =
            TransformationSettings("new", "transformer", TransformationScope.AllDispatchers)
        val duplicated =
            newTransformation.copy(configuration = DataObject.create { put("different", "config") })
        transformerCoordinator.registerTransformation(newTransformation)
        transformerCoordinator.unregisterTransformation(duplicated)

        assertFalse(transformerCoordinator.sortedTransformations.contains(newTransformation))
    }

    @Test
    fun unregisterTransformation_Does_Nothing_When_No_Matching_Transformation() {
        val oldTransformations = transformerCoordinator.sortedTransformations
        val newTransformation =
            TransformationSettings("new", "transformer", TransformationScope.AllDispatchers)
        transformerCoordinator.unregisterTransformation(newTransformation)

        assertEquals(oldTransformations, transformerCoordinator.sortedTransformations)
    }

    @Test
    fun transform_Does_Evaluate_Transformation_When_Condition_Matches() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put("number", 50)
        })
        val addedSettings = addTransformation(
            conditions = Rule.just(
                isGreaterThan(false, "number", "10")
            )
        )

        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        verify {
            mockTransformer4.applyTransformation(addedSettings, dispatch, any(), any())
        }
    }

    @Test
    fun transform_Does_Not_Evaluate_Transformation_When_Condition_Not_Matches() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put("number", 5)
        })
        val addedSettings = addTransformation(
            conditions = Rule.just(
                isGreaterThan(false, "number", "10")
            )
        )

        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(inverse = true) {
            mockTransformer4.applyTransformation(addedSettings, dispatch, any(), any())
        }
    }

    @Test
    fun transform_Does_Not_Evaluate_Transformation_When_Invalid_Condition() {
        val addedSettings = addTransformation(
            conditions = Rule.just(
                isGreaterThan(false, "number", "not-a-number")
            )
        )

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)

        verify(inverse = true) {
            mockTransformer4.applyTransformation(addedSettings, any(), any(), any())
        }
    }

    @Test
    fun transform_Does_Not_Throw_When_Invalid_Condition() {
        addTransformation(
            conditions = Rule.just(
                isGreaterThan(false, "number", "not-a-number")
            )
        )

        transformerCoordinator.transform(mockDispatch, DispatchScope.AfterCollectors, onTransformed)
    }

    @Test
    fun transform_Evaluates_Conditions_With_Result_Of_Previous_Transformations() {
        val dispatch = Dispatch.create("event")
        val first =
            addTransformation("first", conditions = Rule.just(isDefined("run_first")), order = 1)
        val second =
            addTransformation("second", conditions = Rule.just(isDefined("run_second")), order = 2)
        val third =
            addTransformation("third", conditions = Rule.just(isDefined("run_third")), order = 3)

        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        // no conditions met yet
        verify(inverse = true) {
            mockTransformer4.applyTransformation(first, any(), any(), any())
            mockTransformer4.applyTransformation(second, any(), any(), any())
            mockTransformer4.applyTransformation(third, any(), any(), any())
        }

        // satisfy the `first` condition
        dispatch.addAll(DataObject.create { put("run_first", true) })
        // mock the transformations to satisfy the `second` and `third conditions
        mockTransformation(first, mockTransformer4) { dispatch ->
            dispatch.apply {
                addAll(DataObject.create { put("run_second", true) })
            }
        }
        mockTransformation(second, mockTransformer4) { dispatch ->
            dispatch.apply {
                addAll(DataObject.create { put("run_third", true) })
            }
        }
        mockTransformation(third, mockTransformer4) { dispatch ->
            dispatch.apply {
                addAll(DataObject.create { put("third_ran", true) })
            }
        }

        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        verifyOrder {
            mockTransformer4.applyTransformation(first, any(), any(), any())
            mockTransformer4.applyTransformation(second, any(), any(), any())
            mockTransformer4.applyTransformation(third, any(), any(), any())
            onTransformed.invoke(match {
                val payload = it.payload()
                payload.getBoolean("run_first")!!
                        && payload.getBoolean("run_second")!!
                        && payload.getBoolean("run_third")!!
                        && payload.getBoolean("third_ran")!!
            })
        }
    }

    @Test
    fun transform_Evaluates_Transformations_In_Order_Specified_On_Settings() {
        val dispatch = Dispatch.create("event")
        val third = addTransformation("third", mockTransformer4.id, order = 3)
        val second = addTransformation("second", mockTransformer4.id, order = 2)
        val first = addTransformation("first", mockTransformer4.id, order = 1)

        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        verifyOrder {
            mockTransformer4.applyTransformation(first, any(), any(), any())
            mockTransformer4.applyTransformation(second, any(), any(), any())
            mockTransformer4.applyTransformation(third, any(), any(), any())
        }
    }

    @Test
    fun transform_Evaluates_Registered_Transformations() {
        val dispatch = Dispatch.create("event")
        val first = TransformationSettings(
            "first",
            mockTransformer4.id,
            TransformationScope.AfterCollectors,
        )

        transformerCoordinator.registerTransformation(first)
        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        verifyOrder {
            mockTransformer4.applyTransformation(first, any(), any(), any())
        }
    }

    @Test
    fun transform_Evaluates_Additional_Transformations_In_Order_Specified_On_Settings() {
        val dispatch = Dispatch.create("event")
        val first = TransformationSettings(
            "first",
            mockTransformer4.id,
            TransformationScope.AfterCollectors,
            order = 1
        )
        val second = first.copy(id = "second", order = 2)
        val third = first.copy(id = "third", order = 3)

        transformerCoordinator.registerTransformation(third)
        transformerCoordinator.registerTransformation(second)
        transformerCoordinator.registerTransformation(first)
        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        verifyOrder {
            mockTransformer4.applyTransformation(first, any(), any(), any())
            mockTransformer4.applyTransformation(second, any(), any(), any())
            mockTransformer4.applyTransformation(third, any(), any(), any())
        }
    }

    @Test
    fun transform_Evaluates_Combined_Transformations_In_Order_Specified_On_Settings() {
        val dispatch = Dispatch.create("event")
        val firstFromSettings =
            addTransformation("first-from-settings", mockTransformer4.id, order = 1)
        val secondFromSettings =
            addTransformation("second-from-settings", mockTransformer4.id, order = 2)
        val firstProgrammatic = firstFromSettings.copy(id = "first-programmatic", order = 1)
        val secondProgrammatic = secondFromSettings.copy(id = "second-programmatic", order = 2)

        transformerCoordinator.registerTransformation(firstProgrammatic)
        transformerCoordinator.registerTransformation(secondProgrammatic)
        transformerCoordinator.transform(dispatch, DispatchScope.AfterCollectors, onTransformed)

        verifyOrder {
            mockTransformer4.applyTransformation(firstFromSettings, any(), any(), any())
            mockTransformer4.applyTransformation(firstProgrammatic, any(), any(), any())
            mockTransformer4.applyTransformation(secondFromSettings, any(), any(), any())
            mockTransformer4.applyTransformation(secondProgrammatic, any(), any(), any())
        }
    }

    @Test
    fun sortedTransformations_Contains_Both_Configured_And_Registered_Transformations() {
        val additionalTransformation =
            TransformationSettings("test", "test", TransformationScope.AfterCollectors)
        transformerCoordinator.registerTransformation(additionalTransformation)

        assertTrue(
            transformerCoordinator.sortedTransformations.containsAll(
                defaultTransformationSettings
            )
        )
        assertTrue(transformerCoordinator.sortedTransformations.contains(additionalTransformation))
    }

    @Test
    fun sortedTransformations_Combines_Both_Registered_And_Configured_Transformations_Ordered_By_Order() {
        transformationsSettings.onNext(emptyList()) // clear configured

        val firstFromSettings =
            addTransformation("first-from-settings", mockTransformer4.id, order = 1)
        val secondFromSettings =
            addTransformation("second-from-settings", mockTransformer4.id, order = 2)
        val firstProgrammatic = firstFromSettings.copy(id = "first-programmatic", order = 1)
        val secondProgrammatic = secondFromSettings.copy(id = "second-programmatic", order = 2)
        transformationsSettings.onNext(listOf(firstFromSettings, secondFromSettings))
        transformerCoordinator.registerTransformation(firstProgrammatic)
        transformerCoordinator.registerTransformation(secondProgrammatic)

        assertEquals(
            listOf(firstFromSettings, firstProgrammatic, secondFromSettings, secondProgrammatic),
            transformerCoordinator.sortedTransformations
        )
    }

    @Test
    fun sortedTransformations_Prefers_Configured_Transformations_When_Order_Clashes() {
        transformationsSettings.onNext(emptyList()) // clear configured

        val firstFromSettings =
            addTransformation("first-from-settings", mockTransformer4.id, order = 1)
        val firstProgrammatic = firstFromSettings.copy(id = "first-programmatic", order = 1)

        transformerCoordinator.registerTransformation(firstProgrammatic)
        transformationsSettings.onNext(listOf(firstFromSettings))

        assertEquals(
            listOf(firstFromSettings, firstProgrammatic),
            transformerCoordinator.sortedTransformations
        )
    }

    @Test
    fun sortedTransformations_Runs_Configured_Unordered_Transformations_Before_Registered_Unordered_Transformations() {
        transformationsSettings.onNext(emptyList()) // clear configured

        val firstFromSettings =
            addTransformation("first-from-settings", mockTransformer4.id)
        val firstProgrammatic = firstFromSettings.copy(id = "first-programmatic")
        val secondProgrammatic = firstFromSettings.copy(id = "second-programmatic")

        transformerCoordinator.registerTransformation(firstProgrammatic)
        transformerCoordinator.registerTransformation(secondProgrammatic)

        assertEquals(
            listOf(firstFromSettings, firstProgrammatic, secondProgrammatic),
            transformerCoordinator.sortedTransformations
        )
    }

    @Test
    fun sortedTransformations_Falls_Back_To_Insertion_Order_When_Order_Property_Not_Set() {
        transformationsSettings.onNext(emptyList()) // clear configured

        val firstProgrammatic = TransformationSettings(
            "first-programmatic", "test",
            TransformationScope.AfterCollectors
        )
        val secondProgrammatic = firstProgrammatic.copy(id = "second-programmatic")
        val thirdProgrammatic = firstProgrammatic.copy(id = "third-programmatic")

        transformerCoordinator.registerTransformation(firstProgrammatic)
        transformerCoordinator.registerTransformation(secondProgrammatic)
        transformerCoordinator.registerTransformation(thirdProgrammatic)

        assertEquals(
            listOf(firstProgrammatic, secondProgrammatic, thirdProgrammatic),
            transformerCoordinator.sortedTransformations
        )
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

    /**
     * Utility method to assign a transformation [block] behavior to a [transformer] for a given
     * set of [settings]. This effectively assigns a new transformation behavior for the given [settings]
     * if it happens to be executed on the given [transformer] during test.
     *
     * The [transformer] should be one created via MockK
     */
    private fun mockTransformation(
        settings: TransformationSettings,
        transformer: Transformer,
        block: (Dispatch) -> Dispatch?
    ) {
        every { transformer.applyTransformation(settings, any(), any(), any()) } answers {
            val transformedDispatch = block.invoke(secondArg())

            val completion = arg<(Dispatch?) -> Unit>(3)
            completion.invoke(transformedDispatch)
        }
    }
}
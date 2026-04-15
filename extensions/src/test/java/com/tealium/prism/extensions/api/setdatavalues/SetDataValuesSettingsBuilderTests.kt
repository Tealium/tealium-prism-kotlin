package com.tealium.prism.extensions.api.setdatavalues

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.extensions.internal.SET_DATA_VALUES
import com.tealium.prism.extensions.internal.setdatavalues.SetDataValuesConfiguration.Converter
import com.tealium.prism.extensions.internal.setdatavalues.SetDataValuesOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetDataValuesSettingsBuilderTests {

    val converter = SetDataValuesOperation.Converter
    val transformationId = "test-transformation"

    @Test
    fun constructor_SetsCorrectTransformationId() {
        val builder = SetDataValuesSettingsBuilder(transformationId)

        val settings = builder.build()

        assertEquals(transformationId, settings.id)
        assertEquals(SET_DATA_VALUES, settings.transformerId)
    }

    @Test
    fun addScope_SingleScope_AddsToScopes() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val scope = TransformationScope.AfterCollectors

        val settings = builder.addScope(scope).build()

        assertTrue(settings.scope.contains(scope))
    }

    @Test
    fun addScope_MultipleScopes_AddsAllScopes() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val scope1 = TransformationScope.AfterCollectors
        val scope2 = TransformationScope.AllDispatchers

        val settings = builder
            .addScope(scope1)
            .addScope(scope2)
            .build()

        assertTrue(settings.scope.contains(scope1))
        assertTrue(settings.scope.contains(scope2))
        assertEquals(2, settings.scope.size)
    }

    @Test
    fun setFrom_ReturnsExpectedParameters() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val input = ReferenceContainer.key("input_key")
        val destination = ReferenceContainer.key("destination_key")

        val settings = builder.setFrom(input, destination).build()
        val config = settings.configuration
        val operations = config.operations

        val expectedOperations = listOf(
            SetDataValuesOperation(ValueSource.Reference(input), destination)
        )

        assertEquals(1, operations?.size)
        assertEquals(expectedOperations, operations)
    }

    @Test
    fun setConstant_ReturnsExpectParameters() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val input = "constant-value".asDataItem()
        val destination = ReferenceContainer.key("destination_key")

        val settings = builder.setConstant(input, destination).build()
        val config = settings.configuration
        val operations = config.operations

        val expectedOperations = listOf(
            SetDataValuesOperation(
                ValueSource.Constant(ValueContainer(input.asDataItem())),
                ReferenceContainer.key("destination_key")
            )
        )

        assertEquals(1, operations?.size)
        assertEquals(expectedOperations, operations)
    }

    @Test
    fun setFrom_MultipleOperations_AddsAllOperations() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val input1 = ReferenceContainer.key("input1_key")
        val destination1 = ReferenceContainer.key("destination1_key")
        val input2 = "constant-value".asDataItem()
        val destination2 = ReferenceContainer.key("destination2_key")

        val settings = builder
            .setFrom(input1, destination1)
            .setConstant(input2, destination2)
            .build()
        val config = settings.configuration
        val operations = config.operations

        val expectedOperations = listOf(
            SetDataValuesOperation(
                ValueSource.Reference(input1),
                ReferenceContainer.key("destination1_key"),
            ),
            SetDataValuesOperation(
                ValueSource.Constant(ValueContainer(input2.asDataItem())),
                ReferenceContainer.key("destination2_key")
            )
        )

        assertEquals(2, operations?.size)
        assertEquals(expectedOperations, operations)
    }

    @Test
    fun build_WithNoOperations_ProducesNoOperationsConfiguration() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val settings = builder.build()
        val config = settings.configuration
        val operations = config.operations

        assertNull(operations)
    }

    @Test
    fun build_WithAllProperties_CreatesCompleteSettings() {
        val transformationId = "complete-test"
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val scope = TransformationScope.AfterCollectors

        val condition = Rule.just(Condition.isEqual(true, JsonObjectPath["obj"], "true"))
        val input = ReferenceContainer.key("input_key")
        val destination = ReferenceContainer.key("destination_key")

        val settings = builder
            .addScope(scope)
            .setCondition(condition)
            .setFrom(input, destination)
            .build()

        assertEquals(transformationId, settings.id)
        assertEquals(SET_DATA_VALUES, settings.transformerId)
        assertTrue(settings.scope.contains(scope))
        assertEquals(condition, settings.conditions)

        val config = settings.configuration
        val operations = config.operations

        val expectedOperations = listOf(
            SetDataValuesOperation(ValueSource.Reference(input), destination)
        )

        assertEquals(1, operations?.size)
        assertEquals(expectedOperations, operations)
    }

    @Test
    fun setFrom_ReturnsBuilderInstance_AllowsChaining() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val input = ReferenceContainer.key("input_key")
        val destination = ReferenceContainer.key("destination_key")

        val result = builder.setFrom(input, destination)

        assertSame(builder, result)
    }

    @Test
    fun addScope_ReturnsBuilderInstance_AllowsChaining() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val scope = TransformationScope.AfterCollectors
        val result = builder.addScope(scope)

        assertSame(builder, result)
    }

    @Test
    fun setCondition_ReturnsBuilderInstance_AllowsChaining() {
        val builder = SetDataValuesSettingsBuilder(transformationId)
        val condition = Rule.just(Condition.isEqual(true, JsonObjectPath["obj"], "true"))
        val result = builder.setCondition(condition)

        assertSame(builder, result)
    }

    private val DataObject.operations: List<SetDataValuesOperation>?
        get() = getDataList(Converter.KEY_OPERATIONS)?.mapNotNull(converter::convert)
}
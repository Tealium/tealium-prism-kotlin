package com.tealium.prism.extensions.internal.setdatavalues

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.data.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetDataValuesOperationTests {
    private val destination = ReferenceContainer.key("destination_key")

    @Test
    fun asDataItem_WithReference_ReturnsReferenceDataItem() {
        val inputReference = ReferenceContainer.key("test_key")
        val operation = SetDataValuesOperation(ValueSource.Reference(inputReference), destination)
        val result = SetDataValuesOperation.Converter.convert(operation.asDataItem())

        val resultInput = result?.input as ValueSource.Reference
        assertEquals(inputReference, resultInput.reference)
        assertEquals(destination, result.destination)
    }

    @Test
    fun asDataItem_WithConstant_ReturnsValueDataItem() {
        val inputValue = ValueContainer("test_value".asDataItem())
        val operation = SetDataValuesOperation(ValueSource.Constant(inputValue), destination)
        val result = SetDataValuesOperation.Converter.convert(operation.asDataItem())

        val resultInput = result?.input as ValueSource.Constant
        assertEquals(inputValue, resultInput.value)
        assertEquals(destination, result.destination)
    }

    @Test
    fun converter_WithReferenceContainer_ReturnsReferenceInput() {
        val reference = ReferenceContainer.key("test_key")
        val operation = SetDataValuesOperation(ValueSource.Reference(reference), destination)
        val result = SetDataValuesOperation.Converter.convert(operation.asDataItem())

        val resultInput = result?.input as ValueSource.Reference
        assertEquals(reference, resultInput.reference)
        assertEquals(destination, result.destination)
    }

    @Test
    fun converter_WithValueContainer_ReturnsConstantInput() {
        val value = ValueContainer("test_value".asDataItem())
        val operation = SetDataValuesOperation(ValueSource.Constant(value), destination)
        val result = SetDataValuesOperation.Converter.convert(operation.asDataItem())

        val resultInput = result?.input as ValueSource.Constant
        assertEquals(value, resultInput.value)
        assertEquals(destination, result.destination)
    }

    @Test
    fun converter_WithInvalidDataItem_ReturnsNull() {
        val invalidDataItem = DataItem.string("invalid_data")
        val result = SetDataValuesOperation.Converter.convert(invalidDataItem)

        assertNull(result)
    }

    @Test
    fun roundTrip_WithReference_PreservesData() {
        val reference = ValueSource.Reference(ReferenceContainer.key("test_reference"))
        val operation = SetDataValuesOperation(reference, destination)
        val result = SetDataValuesOperation.Converter.convert(operation.asDataItem())

        val resultInput = result?.input as ValueSource.Reference
        assertEquals(reference, resultInput)
        assertEquals(reference.reference, resultInput.reference)
        assertEquals(destination, result.destination)
    }

    @Test
    fun roundTrip_WithConstant_PreservesData() {
        val value = ValueSource.Constant(ValueContainer("constant_test_value".asDataItem()))
        val operation = SetDataValuesOperation(value, destination)
        val result = SetDataValuesOperation.Converter.convert(operation.asDataItem())

        val resultInput = result?.input as ValueSource.Constant
        assertEquals(value, resultInput)
        assertEquals(value.value, resultInput.value)
        assertEquals(destination, result.destination)
    }

    @Test
    fun converter_WithEmptyKey_HandlesCorrectly() {
        val reference = ValueSource.Reference(ReferenceContainer.key(""))
        val operation = SetDataValuesOperation(reference, destination)
        val result = SetDataValuesOperation.Converter.convert(operation.asDataItem())

        val resultInput = result?.input as ValueSource.Reference
        assertEquals(reference.reference, resultInput.reference)
        assertEquals(destination, result.destination)
    }
}

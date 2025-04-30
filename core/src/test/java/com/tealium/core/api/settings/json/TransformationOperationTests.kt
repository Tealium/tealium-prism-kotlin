package com.tealium.core.api.settings.json

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataObjectConvertible
import com.tealium.core.api.settings.VariableAccessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransformationOperationTests {

    @Test
    fun constructor_Sets_Properties_Correctly() {
        val output = VariableAccessor("test_variable", listOf("path1"))
        val parameters = TestParameters("test_value")

        val operation = TransformationOperation(output, parameters)

        assertEquals(output, operation.destination)
        assertEquals(parameters, operation.parameters)
    }

    @Test
    fun asDataObject_Returns_Correct_DataObject() {
        val output = VariableAccessor("test_variable", listOf("path1"))
        val parameters = TestParameters("test_value")
        val operation = TransformationOperation(output, parameters)

        val dataObject = operation.asDataObject()

        assertNotNull(dataObject)
        val outputAccessor = dataObject.get(TransformationOperation.KEY_DESTINATION, VariableAccessor.Converter)
        assertEquals(output, outputAccessor)
        
        val parametersDataObject = dataObject.getDataObject(TransformationOperation.KEY_PARAMETERS)
        assertNotNull(parametersDataObject)
        assertEquals("test_value", parametersDataObject?.getString(TestParameters.KEY_VALUE))
    }

    @Test
    fun converter_Converts_DataObject_To_TransformationOperation_Correctly() {
        val variable = "test_variable"
        val path = listOf("path1")
        val paramValue = "test_value"

        val dataObject = DataObject.create {
            put(TransformationOperation.KEY_DESTINATION, VariableAccessor(variable, path))
            put(TransformationOperation.KEY_PARAMETERS, TestParameters(paramValue))
        }

        val converter = TransformationOperation.Converter(TestParameters.Converter)
        val operation = converter.convert(dataObject.asDataItem())!!

        assertNotNull(operation)
        assertEquals(variable, operation.destination.variable)
        assertEquals(path, operation.destination.path)
        assertEquals(paramValue, operation.parameters.value)
    }

    @Test
    fun converter_Returns_Null_When_DataObject_Is_Null() {
        val converter = TransformationOperation.Converter(TestParameters.Converter)
        val operation = converter.convert(DataItem.NULL)

        assertNull(operation)
    }

    @Test
    fun converter_Returns_Null_When_Output_Is_Missing() {
        val dataObject = DataObject.create {
            put(TransformationOperation.KEY_PARAMETERS, TestParameters("test_value"))
        }

        val converter = TransformationOperation.Converter(TestParameters.Converter)
        val operation = converter.convert(dataObject.asDataItem())

        assertNull(operation)
    }

    @Test
    fun converter_Returns_Null_When_Parameters_Is_Missing() {
        val dataObject = DataObject.create {
            put(TransformationOperation.KEY_DESTINATION, VariableAccessor("test_variable", listOf("path1")))
        }

        val converter = TransformationOperation.Converter(TestParameters.Converter)
        val operation = converter.convert(dataObject.asDataItem())

        assertNull(operation)
    }

    @Test
    fun equals_And_HashCode_Are_Correct() {
        val output1 = VariableAccessor("variable1", listOf("path1"))
        val output2 = VariableAccessor("variable2", listOf("path2"))
        val params1 = TestParameters("value1")
        val params2 = TestParameters("value2")

        val op1 = TransformationOperation(output1, params1)
        val op2 = TransformationOperation(output1, params1)
        val op3 = TransformationOperation(output2, params1)
        val op4 = TransformationOperation(output1, params2)

        // Test equals
        assertEquals(op1, op2)
        assertNotEquals(op1, op3)
        assertNotEquals(op1, op4)

        // Test hashCode
        assertEquals(op1.hashCode(), op2.hashCode())
        assertNotEquals(op1.hashCode(), op3.hashCode())
        assertNotEquals(op1.hashCode(), op4.hashCode())
    }

    // Test implementation of DataItemConvertible for use in tests
    private data class TestParameters(val value: String) : DataObjectConvertible {
        override fun asDataObject(): DataObject = DataObject.create {
            put(KEY_VALUE, value)
        }

        companion object {
            const val KEY_VALUE = "value"
        }

        object Converter : DataItemConverter<TestParameters> {
            override fun convert(dataItem: DataItem): TestParameters? {
                val dataObject = dataItem.getDataObject() ?: return null
                val value = dataObject.getString(KEY_VALUE) ?: return null
                return TestParameters(value)
            }
        }
    }
}

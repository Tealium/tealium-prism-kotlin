package com.tealium.prism.core.api.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FailableExtractionTests {

    // region extract

    @Test
    fun extract_Returns_Null_When_Key_Is_Absent() {
        assertNull(DataObject.EMPTY_OBJECT.extract(Key.VALUE, LenientConverters.INT))
    }

    @Test
    fun extract_Returns_Null_Without_Throwing_When_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertNull(payload.extract(Key.VALUE, LenientConverters.INT))
    }

    @Test
    fun extract_Returns_Converted_Value_When_Value_Is_Present() {
        val payload = DataObject.create { put("value", "42") }

        assertEquals(42, payload.extract(Key.VALUE, LenientConverters.INT))
    }

    @Test
    fun extract_Reads_Nested_Path() {
        val payload = DataObject.create {
            put("parameters", DataObject.create { put("user_id", "42") })
        }

        assertEquals(42, payload.extract(Key.USER_ID, LenientConverters.INT))
    }

    // endregion

    // region extractDataObject

    @Test
    fun extractDataObject_Returns_Null_When_Key_Is_Absent() {
        assertNull(DataObject.EMPTY_OBJECT.extractDataObject(Key.VALUE))
    }

    @Test
    fun extractDataObject_Returns_Null_Without_Throwing_When_Value_Is_Not_An_Object() {
        val payload = DataObject.create { put("value", "text") }

        assertNull(payload.extractDataObject(Key.VALUE))
    }

    @Test
    fun extractDataObject_Returns_Object_When_Value_Is_An_Object() {
        val expected = DataObject.create { put("inner", "x") }
        val payload = DataObject.create { put("value", expected) }

        assertEquals(expected, payload.extractDataObject(Key.VALUE))
    }

    // endregion

    // region raw paths

    @Test
    fun requireOneOf_Matches_Raw_Child_Path() {
        val payload = parentWithChild(DataItem.string("GRANTED"))

        assertEquals(Mode.GRANTED, payload.requireOneOf(rawChildPath, Mode.values))
    }

    @Test
    fun requireOneOf_Renders_Raw_Child_Path_In_Error_Message() {
        val payload = parentWithChild(DataItem.string("other"))

        val error = assertThrows(ExtractionException::class.java) {
            payload.requireOneOf(rawChildPath, Mode.values)
        }

        assertInvalidValue(error, "parent.child", "expected one of denied, granted, unknown")
    }

    @Test
    fun optionalOneOf_Matches_Raw_Child_Path() {
        val payload = parentWithChild(DataItem.string("Denied"))

        assertEquals(Mode.DENIED, payload.optionalOneOf(rawChildPath, Mode.values))
        assertNull(payload.optionalOneOf(JsonPath["parent"].key("other"), Mode.values))
    }

    @Test
    fun optionalOneOf_Renders_Raw_Child_Path_In_Error_Message() {
        val payload = parentWithChild(DataItem.string("other"))

        val error = assertThrows(ExtractionException::class.java) {
            payload.optionalOneOf(rawChildPath, Mode.values)
        }

        assertInvalidValue(error, "parent.child", "expected one of denied, granted, unknown")
    }

    @Test
    fun optional_Reads_Raw_Child_Path() {
        val payload = parentWithChild(DataItem.string("text"))

        assertEquals("text", payload.optional(rawChildPath, LenientConverters.STRING))
    }

    @Test
    fun optional_Reads_Raw_Child_Path_With_Expected_Type() {
        val payload = parentWithChild(DataItem.int(1))

        assertEquals("custom-1", payload.optional(rawChildPath, ToStringConverter, "text"))
    }

    @Test
    fun optionalDataObject_Reads_Raw_Child_Path() {
        val child = DataObject.create { put("inner", "x") }
        val payload = parentWithChild(child.asDataItem())

        assertEquals(child, payload.optionalDataObject(rawChildPath))
    }

    @Test
    fun requireDataItem_Reads_Raw_Child_Path() {
        val payload = parentWithChild(DataItem.string("text"))

        assertEquals(DataItem.string("text"), payload.requireDataItem(rawChildPath))
    }

    @Test
    fun require_Reads_Raw_Child_Path() {
        val payload = parentWithChild(DataItem.string("text"))

        assertEquals("text", payload.require(rawChildPath, LenientConverters.STRING))
    }

    @Test
    fun require_Reads_Raw_Child_Path_With_Expected_Type() {
        val payload = parentWithChild(DataItem.int(1))

        assertEquals("custom-1", payload.require(rawChildPath, ToStringConverter, "text"))
    }

    @Test
    fun require_Renders_Raw_Child_Path_In_Error_Message() {
        assertMissing("parent.child") {
            DataObject.EMPTY_OBJECT.require(rawChildPath, LenientConverters.STRING)
        }
    }

    @Test
    fun requireDataObject_Reads_Raw_Child_Path() {
        val child = DataObject.create { put("inner", "x") }
        val payload = parentWithChild(child.asDataItem())

        assertEquals(child, payload.requireDataObject(rawChildPath))
    }

    @Test
    fun requireNonBlank_Reads_Raw_Child_Path() {
        val payload = parentWithChild(DataItem.string("text"))

        assertEquals("text", payload.requireNonBlank(rawChildPath, LenientConverters.STRING))
    }

    // endregion

    // region oneOf with mixed case keys

    @Test
    fun requireOneOf_Sorts_Mixed_Case_Keys_In_Error_Message() {
        val levels = mapOf("low" to 1, "MAX" to 3, "high" to 2)
        val payload = DataObject.create { put("value", "other") }

        val error = assertThrows(ExtractionException::class.java) {
            payload.requireOneOf(Key.VALUE, levels)
        }

        assertInvalidValue(error, "value", "expected one of high, low, MAX")
    }

    @Test
    fun requireOneOf_Returns_First_Value_When_Keys_Differ_Only_By_Case() {
        val modes = mapOf("mode" to 1, "MODE" to 2)
        val payload = DataObject.create { put("value", "Mode") }

        assertEquals(1, payload.requireOneOf(Key.VALUE, modes))
    }

    @Test
    fun optionalOneOf_Returns_First_Value_When_Keys_Differ_Only_By_Case() {
        val modes = mapOf("mode" to 1, "MODE" to 2)
        val payload = DataObject.create { put("value", "Mode") }

        assertEquals(1, payload.optionalOneOf(Key.VALUE, modes))
    }

    // endregion

    // region requireDataItem

    @Test
    fun requireDataItem_Throws_Missing_When_Key_Is_Absent() {
        assertMissing("value") { DataObject.EMPTY_OBJECT.requireDataItem(Key.VALUE) }
    }

    @Test
    fun requireDataItem_Returns_Item_When_Key_Is_Present() {
        val payload = DataObject.create { put("value", "text") }

        assertEquals(DataItem.string("text"), payload.requireDataItem(Key.VALUE))
    }

    @Test
    fun requireDataItem_Renders_Nested_Path_In_Error_Message() {
        assertMissing("parameters.user_id") { DataObject.EMPTY_OBJECT.requireDataItem(Key.USER_ID) }
    }

    @Test
    fun requireDataItem_Returns_Null_Item_When_Value_Is_Explicit_Null() {
        val payload = DataObject.create { putNull("value") }

        assertEquals(DataItem.NULL, payload.requireDataItem(Key.VALUE))
    }

    // endregion

    // region require (reified)

    @Test
    fun require_Throws_Missing_When_Key_Is_Absent() {
        assertMissing("value") { DataObject.EMPTY_OBJECT.require(Key.VALUE, LenientConverters.STRING) }
    }

    @Test
    fun require_Renders_Nested_Path_In_Error_Message() {
        assertMissing("parameters.user_id") {
            DataObject.EMPTY_OBJECT.require(Key.USER_ID, LenientConverters.STRING)
        }
    }

    @Test
    fun require_Uses_Simple_Type_Name_When_String_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", DataObject.EMPTY_OBJECT) }

        assertInvalid("value", "String") { payload.require(Key.VALUE, LenientConverters.STRING) }
    }

    @Test
    fun require_Uses_Simple_Type_Name_When_Boolean_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertInvalid("value", "Boolean") { payload.require(Key.VALUE, LenientConverters.BOOLEAN) }
    }

    @Test
    fun require_Uses_Simple_Type_Name_When_Double_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertInvalid("value", "Double") { payload.require(Key.VALUE, LenientConverters.DOUBLE) }
    }

    @Test
    fun require_Uses_Simple_Type_Name_When_Int_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertInvalid("value", "Int") { payload.require(Key.VALUE, LenientConverters.INT) }
    }

    @Test
    fun require_Uses_Simple_Type_Name_When_Long_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertInvalid("value", "Long") { payload.require(Key.VALUE, LenientConverters.LONG) }
    }

    @Test
    fun require_Converts_Leniently_When_String_Value_Is_Number() {
        val payload = DataObject.create { put("value", 1) }

        assertEquals("1", payload.require(Key.VALUE, LenientConverters.STRING))
    }

    @Test
    fun require_Converts_Leniently_When_Boolean_Value_Is_String() {
        val payload = DataObject.create { put("value", "true") }

        assertEquals(true, payload.require(Key.VALUE, LenientConverters.BOOLEAN))
    }

    @Test
    fun require_Converts_Leniently_When_Double_Value_Is_String() {
        val payload = DataObject.create { put("value", "1.5") }

        assertEquals(1.5, payload.require(Key.VALUE, LenientConverters.DOUBLE), 0.0)
    }

    @Test
    fun require_Converts_Leniently_When_Int_Value_Is_String() {
        val payload = DataObject.create { put("value", "42") }

        assertEquals(42, payload.require(Key.VALUE, LenientConverters.INT))
    }

    @Test
    fun require_Converts_Leniently_When_Long_Value_Is_String() {
        val payload = DataObject.create { put("value", "42") }

        assertEquals(42L, payload.require(Key.VALUE, LenientConverters.LONG))
    }

    @Test
    fun require_Truncates_Decimals_When_Using_Lenient_Int_Converter() {
        val payload = DataObject.create { put("value", 1.5) }

        assertEquals(1, payload.require(Key.VALUE, LenientConverters.INT))
    }

    @Test
    fun require_Uses_Custom_Converter_When_Provided() {
        val payload = DataObject.create { put("value", 1.5) }

        assertInvalid("value", "Int") { payload.require(Key.VALUE, StrictIntConverter) }
    }

    @Test
    fun require_Throws_Missing_When_Value_Is_Explicit_Null() {
        val payload = DataObject.create { putNull("value") }

        assertMissing("value") { payload.require(Key.VALUE, LenientConverters.STRING) }
    }

    // endregion

    // region require (expectedType)

    @Test
    fun require_Throws_Missing_When_Value_Is_Explicit_Null_With_Expected_Type() {
        val payload = DataObject.create { putNull("value") }

        assertMissing("value") { payload.require(Key.VALUE, ToStringConverter, "text") }
    }

    @Test
    fun require_Throws_Missing_When_Key_Is_Absent_With_Expected_Type() {
        assertMissing("value") {
            DataObject.EMPTY_OBJECT.require(Key.VALUE, NullConverter, "text")
        }
    }

    @Test
    fun require_Uses_Given_Expected_Type_When_Converter_Returns_Null() {
        val payload = DataObject.create { put("value", 1) }

        assertInvalid("value", "text") { payload.require(Key.VALUE, NullConverter, "text") }
    }

    @Test
    fun require_Returns_Converted_Value_When_Expected_Type_Is_Given() {
        val payload = DataObject.create { put("value", 1) }

        assertEquals("custom-1", payload.require(Key.VALUE, ToStringConverter, "text"))
    }

    // endregion

    // region requireNonBlank

    @Test
    fun requireNonBlank_Throws_Missing_When_Key_Is_Absent() {
        assertMissing("value") { DataObject.EMPTY_OBJECT.requireNonBlank(Key.VALUE, LenientConverters.STRING) }
    }

    @Test
    fun requireNonBlank_Throws_Missing_When_Value_Is_Explicit_Null() {
        val payload = DataObject.create { putNull("value") }

        assertMissing("value") { payload.requireNonBlank(Key.VALUE, LenientConverters.STRING) }
    }

    @Test
    fun requireNonBlank_Throws_TypeMismatch_When_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", DataObject.EMPTY_OBJECT) }

        assertInvalid("value", "String") { payload.requireNonBlank(Key.VALUE, LenientConverters.STRING) }
    }

    @Test
    fun requireNonBlank_Throws_Empty_When_Value_Is_Blank() {
        listOf("", " ", "   ", "\t", "\n", " \t\n ").forEach { blank ->
            val payload = DataObject.create { put("value", blank) }

            val error = assertThrows(ExtractionException::class.java) {
                payload.requireNonBlank(Key.VALUE, LenientConverters.STRING)
            }

            assertEmpty(error, "value")
        }
    }

    @Test
    fun requireNonBlank_Returns_Value_Untrimmed_When_Value_Is_Not_Blank() {
        val payload = DataObject.create { put("value", "  abc ") }

        assertEquals("  abc ", payload.requireNonBlank(Key.VALUE, LenientConverters.STRING))
    }

    @Test
    fun requireNonBlank_Renders_Nested_Path_In_Error_Message() {
        val payload = DataObject.create {
            put("parameters", DataObject.create { put("user_id", " ") })
        }

        val error = assertThrows(ExtractionException::class.java) {
            payload.requireNonBlank(Key.USER_ID, LenientConverters.STRING)
        }

        assertEmpty(error, "parameters.user_id")
    }

    // endregion

    // region requireOneOf

    @Test
    fun requireOneOf_Throws_Missing_When_Key_Is_Absent() {
        assertMissing("value") { DataObject.EMPTY_OBJECT.requireOneOf(Key.VALUE, Mode.values) }
    }

    @Test
    fun requireOneOf_Throws_Missing_When_Value_Is_Explicit_Null() {
        val payload = DataObject.create { putNull("value") }

        assertMissing("value") { payload.requireOneOf(Key.VALUE, Mode.values) }
    }

    @Test
    fun requireOneOf_Throws_Empty_When_Value_Is_Blank() {
        listOf("", " ", "\t", "\n", " \t\n ").forEach { blank ->
            val payload = DataObject.create { put("value", blank) }

            val error = assertThrows(ExtractionException::class.java) {
                payload.requireOneOf(Key.VALUE, Mode.values)
            }

            assertEmpty(error, "value")
        }
    }

    @Test
    fun requireOneOf_Throws_TypeMismatch_When_Value_Is_Not_Convertible_To_String() {
        val objectPayload = DataObject.create { put("value", DataObject.EMPTY_OBJECT) }
        val arrayPayload = DataObject.create { put("value", DataList.EMPTY_LIST) }

        assertInvalid("value", "String") { objectPayload.requireOneOf(Key.VALUE, Mode.values) }
        assertInvalid("value", "String") { arrayPayload.requireOneOf(Key.VALUE, Mode.values) }
    }

    @Test
    fun requireOneOf_Throws_InvalidValue_With_Sorted_Keys_When_Value_Is_Unknown() {
        val payload = DataObject.create { put("value", "other") }

        val error = assertThrows(ExtractionException::class.java) {
            payload.requireOneOf(Key.VALUE, Mode.values)
        }

        assertInvalidValue(error, "value", "expected one of denied, granted, unknown")
    }

    @Test
    fun requireOneOf_Does_Not_Include_Received_Value_In_Error_Message() {
        val payload = DataObject.create { put("value", "secret@example.com") }

        val error = assertThrows(ExtractionException::class.java) {
            payload.requireOneOf(Key.VALUE, Mode.values)
        }

        assertFalse(error.message!!.contains("secret@example.com"))
    }

    @Test
    fun requireOneOf_Matches_Ignoring_Case_And_Surrounding_Whitespace() {
        listOf("granted", "GRANTED", "Granted", "  granted ", "\tgranted\n").forEach { text ->
            val payload = DataObject.create { put("value", text) }

            assertEquals(Mode.GRANTED, payload.requireOneOf(Key.VALUE, Mode.values))
        }
    }

    @Test
    fun requireOneOf_Renders_Nested_Path_In_Error_Message() {
        val payload = DataObject.create {
            put("parameters", DataObject.create { put("user_id", "other") })
        }

        val error = assertThrows(ExtractionException::class.java) {
            payload.requireOneOf(Key.USER_ID, Mode.values)
        }

        assertInvalidValue(error, "parameters.user_id", "expected one of denied, granted, unknown")
    }

    // endregion

    // region requireDataObject

    @Test
    fun requireDataObject_Throws_Missing_When_Key_Is_Absent() {
        assertMissing("value") { DataObject.EMPTY_OBJECT.requireDataObject(Key.VALUE) }
    }

    @Test
    fun requireDataObject_Throws_Missing_When_Value_Is_Explicit_Null() {
        val payload = DataObject.create { putNull("value") }

        assertMissing("value") { payload.requireDataObject(Key.VALUE) }
    }

    @Test
    fun requireDataObject_Throws_TypeMismatch_When_Value_Is_Not_An_Object() {
        val payload = DataObject.create { put("value", "text") }

        assertInvalid("value", "DataObject") { payload.requireDataObject(Key.VALUE) }
    }

    @Test
    fun requireDataObject_Returns_Object_When_Value_Is_An_Object() {
        val expected = DataObject.create { put("inner", "x") }
        val payload = DataObject.create { put("value", expected) }

        assertEquals(expected, payload.requireDataObject(Key.VALUE))
    }

    // endregion

    // region optional (reified)

    @Test
    fun optional_Returns_Null_When_Key_Is_Absent() {
        assertNull(DataObject.EMPTY_OBJECT.optional(Key.VALUE, LenientConverters.STRING))
    }

    @Test
    fun optional_Returns_Null_When_Value_Is_Explicit_Null() {
        val payload = DataObject.create { putNull("value") }

        assertNull(payload.optional(Key.VALUE, LenientConverters.STRING))
    }

    @Test
    fun optional_Uses_Simple_Type_Name_When_String_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", DataObject.EMPTY_OBJECT) }

        assertInvalid("value", "String") { payload.optional(Key.VALUE, LenientConverters.STRING) }
    }

    @Test
    fun optional_Uses_Simple_Type_Name_When_Boolean_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertInvalid("value", "Boolean") { payload.optional(Key.VALUE, LenientConverters.BOOLEAN) }
    }

    @Test
    fun optional_Uses_Simple_Type_Name_When_Double_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertInvalid("value", "Double") { payload.optional(Key.VALUE, LenientConverters.DOUBLE) }
    }

    @Test
    fun optional_Uses_Simple_Type_Name_When_Int_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertInvalid("value", "Int") { payload.optional(Key.VALUE, LenientConverters.INT) }
    }

    @Test
    fun optional_Uses_Simple_Type_Name_When_Long_Value_Is_Not_Convertible() {
        val payload = DataObject.create { put("value", "abc") }

        assertInvalid("value", "Long") { payload.optional(Key.VALUE, LenientConverters.LONG) }
    }

    @Test
    fun optional_Returns_Converted_Value_When_Value_Is_Present() {
        val payload = DataObject.create { put("value", "42") }

        assertEquals(42, payload.optional(Key.VALUE, LenientConverters.INT))
    }

    @Test
    fun optional_Uses_Custom_Converter_When_Provided() {
        val payload = DataObject.create { put("value", 1.5) }

        assertInvalid("value", "Int") { payload.optional(Key.VALUE, StrictIntConverter) }
    }

    @Test
    fun optional_Renders_Nested_Path_In_Error_Message() {
        val payload = DataObject.create {
            put("parameters", DataObject.create { put("user_id", "abc") })
        }

        assertInvalid("parameters.user_id", "Int") {
            payload.optional(Key.USER_ID, LenientConverters.INT)
        }
    }

    // endregion

    // region optional (expectedType)

    @Test
    fun optional_Returns_Null_When_Key_Is_Absent_With_Expected_Type() {
        assertNull(DataObject.EMPTY_OBJECT.optional(Key.VALUE, ToStringConverter, "text"))
    }

    @Test
    fun optional_Returns_Null_When_Value_Is_Explicit_Null_With_Expected_Type() {
        val payload = DataObject.create { putNull("value") }

        assertNull(payload.optional(Key.VALUE, ToStringConverter, "text"))
    }

    @Test
    fun optional_Uses_Given_Expected_Type_When_Converter_Returns_Null() {
        val payload = DataObject.create { put("value", 1) }

        assertInvalid("value", "text") { payload.optional(Key.VALUE, NullConverter, "text") }
    }

    @Test
    fun optional_Returns_Converted_Value_When_Expected_Type_Is_Given() {
        val payload = DataObject.create { put("value", 1) }

        assertEquals("custom-1", payload.optional(Key.VALUE, ToStringConverter, "text"))
    }

    // endregion

    // region optionalOneOf

    @Test
    fun optionalOneOf_Returns_Null_When_Key_Is_Absent() {
        assertNull(DataObject.EMPTY_OBJECT.optionalOneOf(Key.VALUE, Mode.values))
    }

    @Test
    fun optionalOneOf_Returns_Null_When_Value_Is_Explicit_Null() {
        val payload = DataObject.create { putNull("value") }

        assertNull(payload.optionalOneOf(Key.VALUE, Mode.values))
    }

    @Test
    fun optionalOneOf_Returns_Null_When_Value_Is_Blank() {
        listOf("", " ", "\t", "\n", " \t\n ").forEach { blank ->
            val payload = DataObject.create { put("value", blank) }

            assertNull(payload.optionalOneOf(Key.VALUE, Mode.values))
        }
    }

    @Test
    fun optionalOneOf_Throws_TypeMismatch_When_Value_Is_Not_Convertible_To_String() {
        val payload = DataObject.create { put("value", DataObject.EMPTY_OBJECT) }

        assertInvalid("value", "String") { payload.optionalOneOf(Key.VALUE, Mode.values) }
    }

    @Test
    fun optionalOneOf_Throws_InvalidValue_With_Sorted_Keys_When_Value_Is_Unknown() {
        val payload = DataObject.create { put("value", "other") }

        val error = assertThrows(ExtractionException::class.java) {
            payload.optionalOneOf(Key.VALUE, Mode.values)
        }

        assertInvalidValue(error, "value", "expected one of denied, granted, unknown")
    }

    @Test
    fun optionalOneOf_Matches_Ignoring_Case_And_Surrounding_Whitespace() {
        val payload = DataObject.create { put("value", "  DENIED\n") }

        assertEquals(Mode.DENIED, payload.optionalOneOf(Key.VALUE, Mode.values))
    }

    // endregion

    // region optionalDataObject

    @Test
    fun optionalDataObject_Returns_Null_When_Key_Is_Absent() {
        assertNull(DataObject.EMPTY_OBJECT.optionalDataObject(Key.VALUE))
    }

    @Test
    fun optionalDataObject_Returns_Null_When_Value_Is_Explicit_Null() {
        val payload = DataObject.create { putNull("value") }

        assertNull(payload.optionalDataObject(Key.VALUE))
    }

    @Test
    fun optionalDataObject_Throws_TypeMismatch_When_Value_Is_Not_An_Object() {
        val payload = DataObject.create { put("value", "text") }

        assertInvalid("value", "DataObject") { payload.optionalDataObject(Key.VALUE) }
    }

    @Test
    fun optionalDataObject_Returns_Object_When_Value_Is_An_Object() {
        val expected = DataObject.create { put("inner", "x") }
        val payload = DataObject.create { put("value", expected) }

        assertEquals(expected, payload.optionalDataObject(Key.VALUE))
    }

    // endregion

    private fun assertMissing(path: String, block: () -> Unit) {
        val error = assertThrows(ExtractionException::class.java) { block() }

        val missing = error as ExtractionException.Missing
        assertEquals(path, missing.key)
    }

    private fun assertInvalid(path: String, expectedType: String, block: () -> Unit) {
        val error = assertThrows(ExtractionException::class.java) { block() }

        val mismatch = error as ExtractionException.TypeMismatch
        assertEquals(path, mismatch.key)
        assertEquals(expectedType, mismatch.expectedType)
    }

    private fun assertInvalidValue(error: ExtractionException, path: String, reason: String) {
        val invalid = error as ExtractionException.InvalidValue
        assertEquals(path, invalid.key)
        assertEquals(reason, invalid.reason)
    }

    private fun assertEmpty(error: ExtractionException, path: String) {
        val empty = error as ExtractionException.Empty
        assertEquals(path, empty.key)
    }

    private val rawChildPath: JsonObjectPath = JsonPath["parent"].key("child")

    private fun parentWithChild(value: DataItem): DataObject =
        DataObject.create {
            put("parent", DataObject.create { put("child", value) })
        }

    private enum class Mode {
        GRANTED, DENIED, UNKNOWN;

        companion object {
            // Deliberately not alphabetical, so the tests prove the error message sorts the keys.
            val values: Map<String, Mode> = linkedMapOf(
                "granted" to GRANTED,
                "denied" to DENIED,
                "unknown" to UNKNOWN
            )
        }
    }

    private enum class Key(private val path: JsonObjectPath) : JsonObjectPathConvertible {
        VALUE(JsonPath.root("value")),
        USER_ID(JsonPath.root("parameters").key("user_id"));

        override fun asJsonObjectPath(): JsonObjectPath = path
    }

    private object ToStringConverter : DataItemConverter<String> {
        override fun convert(dataItem: DataItem): String? = "custom-${dataItem.getInt()}"
    }

    private object NullConverter : DataItemConverter<String> {
        override fun convert(dataItem: DataItem): String? = null
    }

    // Accepts only whole numbers, unlike the lenient converter which truncates decimals.
    private object StrictIntConverter : DataItemConverter<Int> {
        override fun convert(dataItem: DataItem): Int? =
            dataItem.getDouble()?.takeIf { it % 1.0 == 0.0 }?.toInt()
    }
}

package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.tests.common.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonPathTests {

    @Test
    fun toString_Serializes_Single_Key_To_JsonPath_String() {
        val path = JsonPath["list"]
        assertEquals("list", "$path")
    }

    @Test
    fun toString_Serializes_Key_Then_Array_To_JsonPath_String() {
        val path = JsonPath["list"][0]
        assertEquals("list[0]", "$path")
    }

    @Test
    fun toString_Serializes_Key_Then_Special_Key_To_JsonPath_String() {
        val path = JsonPath["obj"]["invalid@char"]
        assertEquals("obj[\"invalid@char\"]", "$path")
    }

    @Test
    fun toString_Serializes_Key_With_Special_Characters_To_JsonPath_String() {
        val path = JsonPath["reserved.char"]
        assertEquals("[\"reserved.char\"]", "$path")
    }

    @Test
    fun toString_Separates_Simple_SubKeys_With_Dot() {
        val path = JsonPath["obj"]["key"]
        assertEquals("obj.key", "$path")
    }

    @Test
    fun toString_Separates_SubKey_Of_Special_Key_With_Dot() {
        val path = JsonPath["invalid@char"]["key"]
        assertEquals("[\"invalid@char\"].key", "$path")
    }

    @Test
    fun toString_Does_Not_Separate_Special_SubKeys_With_Dot() {
        val path = JsonPath[0]["invalid@char"]
        assertEquals("[0][\"invalid@char\"]", "$path")
    }

    @Test
    fun toString_Serializes_Multiple_Array_Indexes_To_JsonPath_String() {
        val path = JsonPath[0][1][2]
        assertEquals("[0][1][2]", "$path")
    }

    @Test
    fun toString_Serializes_Empty_Key_To_Quoted_JsonPath_String() {
        val path = JsonPath[""]
        assertEquals("[\"\"]", "$path")
    }

    @Test
    fun toString_Serializes_Key_Starting_With_Number_To_Quoted_JsonPath_String() {
        val path = JsonPath["123"]
        assertEquals("[\"123\"]", "$path")
    }

    @Test
    fun toString_Wraps_Key_In_Single_Quotes_When_It_Contains_Double_Quote() {
        val path = JsonPath["Double\"Quote"]
        assertEquals("['Double\"Quote']", "$path")
    }

    @Test
    fun toString_Wraps_Key_In_Double_Quotes_When_It_Contains_Single_Quote() {
        val path = JsonPath["Single'Quote"]
        assertEquals("[\"Single'Quote\"]", "$path")
    }

    @Test
    fun toString_Wraps_Key_In_Double_Quotes_And_Escapes_Double_Quotes_When_It_Contains_Single_And_Double_Quotes() {
        val path = JsonPath["\"'"]
        assertEquals("[\"\\\"'\"]", "$path")
    }

    @Test
    fun parse_String_With_SubProperty_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("container.property")
        val expectedPath = JsonPath["container"]["property"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_String_With_Multilingual_Characters_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("cöntáiñer.property")
        val expectedPath = JsonPath["cöntáiñer"]["property"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_String_With_SubProperty_Containing_Numbers_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("container.property123")
        val expectedPath = JsonPath["container"]["property123"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_String_With_SubProperty_Containing_Underscores_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("container.a_property")
        val expectedPath = JsonPath["container"]["a_property"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_String_With_SubProperty_Starting_With_Underscores_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("container._property")
        val expectedPath = JsonPath["container"]["_property"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_String_Starts_With_Numbers() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse("1container")
        }
        assertEquals(0, ex.position)
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_String_With_SubProperty_Starts_With_Numbers() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse("container.1property")
        }
        assertEquals(10, ex.position)
    }

    @Test
    fun parse_String_Ending_With_Array_Index_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("container.array[2]")
        val expectedPath = JsonPath["container"]["array"][2]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Single_Key_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("container")
        val expectedPath = JsonPath["container"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Empty_String() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("")
        }
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_Index_Is_Not_Numeric() {
        val ex = assertThrows<JsonPathSyntaxException>(null) {
            JsonPath.parse("container.array[invalid]")
        }
        assertEquals(16, ex.position)
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Closing_Quote_Not_Found() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("container[\"invalid]")
        }
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Closing_Bracket_Not_Found_At_End_Of_String() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("container[\"invalid\"")
        }
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_Closing_Bracket_Not_Found_In_Middle_Of_String() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse("container[\"invalid\"property")
        }
        assertEquals(19, ex.position)
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Closing_Array_Bracket_Not_Found_At_End_Of_String() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("container[123")
        }
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Trailing_Opening_Bracket_After_Key() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("container[")
        }
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Trailing_Opening_Bracket_After_Quoted_Key() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("[\"container\"][")
        }
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Trailing_Dot_After_Key() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("container.")
        }
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Trailing_Dot_After_Quoted_Key() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("[\"container\"].")
        }
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_Empty_Key_At_Start() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse(".invalid")
        }
        assertEquals(0, ex.position)
    }

    @Test
    fun parse_String_Starting_With_Array_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("[123]")
        val expectedPath = JsonPath[123]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Throws_UnexpectedEndOfInputException_When_Only_Open_Bracket() {
        assertThrows<UnexpectedEndOfInputException> {
            JsonPath.parse("[")
        }
    }

    @Test
    fun parse_String_With_Quoted_Key_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("container[\"reserved.key\"]")
        val expectedPath = JsonPath["container"]["reserved.key"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_String_Starting_With_Quoted_Key_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("[\"reserved.key\"].property")
        val expectedPath = JsonPath["reserved.key"]["property"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_String_With_Multiple_Quoted_Keys_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("[\"reserved.key\"][\"other.key\"]")
        val expectedPath = JsonPath["reserved.key"]["other.key"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_String_With_Multiple_Indexes_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("matrix[123][456]")
        val expectedPath = JsonPath["matrix"][123][456]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_Special_Character_In_Key() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse("invalid-key")
        }
        assertEquals(7, ex.position)
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_Special_Character_In_Middle() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse("valid.key@invalid")
        }
        assertEquals(9, ex.position)
    }

    @Test
    fun parse_Special_Key_Character_In_Quoted_Brackets_Makes_It_Valid() {
        val parsedPath = JsonPath.parse("valid[\"key@valid\"]")
        val expectedPath = JsonPath["valid"]["key@valid"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_Two_Dots() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse("invalid..key")
        }
        assertEquals(8, ex.position)
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_No_Property_Separator() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse("[\"key\"]key")
        }
        assertEquals(7, ex.position)
    }

    @Test
    fun parse_Empty_Quoted_Key_Returns_Valid_JsonPath() {
        val parsedPath = JsonPath.parse("[\"\"]")
        val expectedPath = JsonPath[""]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Throws_JsonPathSyntaxException_When_Empty_Array_Index() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parse("array[]")
        }
        assertEquals(6, ex.position)
    }

    @Test
    fun parse_Supports_Single_Quote_When_Wrapped_In_Double_Quotes() {
        val parsedPath = JsonPath.parse("[\"Qu\'ote\"]")
        val expectedPath = JsonPath["Qu'ote"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Supports_Double_Quote_When_Wrapped_In_Single_Quotes() {
        val parsedPath = JsonPath.parse("['Qu\"ote']")
        val expectedPath = JsonPath["Qu\"ote"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Supports_Escaped_Double_Quote_When_Wrapped_In_Double_Quotes() {
        val parsedPath = JsonPath.parse("[\"Qu\\\"ote\"]")
        val expectedPath = JsonPath["Qu\"ote"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parse_Supports_Escaped_Backslashes_In_Quoted_Key() {
        val parsedPath = JsonPath.parse("[\"Back\\\\slash\"]")
        val expectedPath = JsonPath["Back\\slash"]
        assertEquals(expectedPath, parsedPath)
    }

    @Test
    fun parseJsonObjectPath_Does_Not_Throw_When_First_Component_Is_Key() {
        JsonPath.parseJsonObjectPath("key.subkey")
        JsonPath.parseJsonObjectPath("[\"key\"].subkey")
    }

    @Test
    fun parseJsonObjectPath_Throws_When_First_Component_Is_Index() {
        val ex = assertThrows<JsonPathSyntaxException> {
            JsonPath.parseJsonObjectPath("[1].key")
        }
        assertEquals(0, ex.position)
    }

    @Test
    fun parseJsonListPath_Does_Not_Throw_When_First_Component_Is_Index() {
        JsonPath.parseJsonListPath("[1].key")
        JsonPath.parseJsonListPath("[2].key.subkey")
    }

    @Test
    fun parseJsonListPath_Throws_When_First_Component_Is_Key() {
        val ex1 = assertThrows<JsonPathSyntaxException> {
            JsonPath.parseJsonListPath("obj.key")
        }
        assertEquals(0, ex1.position)
        val ex2 = assertThrows<JsonPathSyntaxException> {
            JsonPath.parseJsonListPath("[\"obj\"].key")
        }
        assertEquals(0, ex2.position)
    }

    @Test
    fun converter_Returns_JsonObjectPath_When_Starts_With_A_Key() {
        val jsonPath = "key".asDataItem()
        val path = JsonPath.Converter.convert(jsonPath)
        val expected = JsonPath["key"]
        assertEquals(expected, path)
    }

    @Test
    fun converter_Returns_JsonListPath_When_Starts_With_An_Index() {
        val jsonPath = "[0]".asDataItem()
        val path = JsonPath.Converter.convert(jsonPath)
        val expected = JsonPath[0]
        assertEquals(expected, path)
    }

    @Test
    fun converter_Returns_Null_When_Invalid_Path() {
        val jsonPath = "1".asDataItem()
        val path = JsonPath.Converter.convert(jsonPath)
        assertNull(path)
    }

    @Test
    fun converter_Returns_Null_When_Not_A_String() {
        val jsonPath = DataList.EMPTY_LIST.asDataItem()
        val path = JsonPath.Converter.convert(jsonPath)
        assertNull(path)
    }
}
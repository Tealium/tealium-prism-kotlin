package com.tealium.core.api.data.bundle

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@Suppress("KotlinConstantConditions")
@RunWith(RobolectricTestRunner::class)
class TealiumValueTests {

    private val string = "string"
    private val int = 10
    private val double = 111.111
    private val long = 100L
    private val booleanFalse = false
    private val booleanTrue = true
    private val list = TealiumList.create {
        add(1)
    }
    private val bundle = TealiumBundle.create {
        put("string", "value")
    }

    private val stringValue: TealiumValue = TealiumValue.string(string)
    private val intValue: TealiumValue = TealiumValue.int(int)
    private val doubleValue: TealiumValue = TealiumValue.double(double)
    private val longValue: TealiumValue = TealiumValue.long(long)
    private val falseValue: TealiumValue = TealiumValue.boolean(booleanFalse)
    private val trueValue: TealiumValue = TealiumValue.boolean(booleanTrue)
    private val listValue: TealiumValue = list.asTealiumValue()
    private val bundleValue: TealiumValue = bundle.asTealiumValue()

    @Test
    fun isBundle_ReturnsTrue_WhenValueIsBundle() {
        assertTrue(bundleValue.isBundle())
    }

    @Test
    fun isBundle_ReturnsFalse_WhenValueIsNotBundle() {
        assertFalse(stringValue.isBundle())
        assertFalse(intValue.isBundle())
        assertFalse(doubleValue.isBundle())
        assertFalse(longValue.isBundle())
        assertFalse(falseValue.isBundle())
        assertFalse(trueValue.isBundle())
        assertFalse(listValue.isBundle())
    }

    @Test
    fun isList_ReturnsTrue_WhenValueIsList() {
        assertTrue(listValue.isList())
    }

    @Test
    fun isList_ReturnsFalse_WhenValueIsNotList() {
        assertFalse(stringValue.isList())
        assertFalse(intValue.isList())
        assertFalse(doubleValue.isList())
        assertFalse(longValue.isList())
        assertFalse(falseValue.isList())
        assertFalse(trueValue.isList())
        assertFalse(bundleValue.isList())
    }

    @Test
    fun isString_ReturnsTrue_WhenValueIsString() {
        assertTrue(stringValue.isString())
    }

    @Test
    fun isString_ReturnsFalse_WhenValueIsNotString() {
        assertFalse(intValue.isString())
        assertFalse(doubleValue.isString())
        assertFalse(longValue.isString())
        assertFalse(falseValue.isString())
        assertFalse(trueValue.isString())
        assertFalse(listValue.isString())
        assertFalse(bundleValue.isString())
    }

    @Test
    fun isInt_ReturnsTrue_WhenValueIsInt() {
        assertTrue(intValue.isInt())
    }

    @Test
    fun isInt_ReturnsFalse_WhenValueIsNotInt() {
        assertFalse(stringValue.isInt())
        assertFalse(doubleValue.isInt())
        assertFalse(longValue.isInt())
        assertFalse(falseValue.isInt())
        assertFalse(trueValue.isInt())
        assertFalse(listValue.isInt())
        assertFalse(bundleValue.isInt())
    }

    @Test
    fun isDouble_ReturnsTrue_WhenValueIsDouble() {
        assertTrue(doubleValue.isDouble())
    }

    @Test
    fun isDouble_ReturnsFalse_WhenValueIsNotDouble() {
        assertFalse(stringValue.isDouble())
        assertFalse(intValue.isDouble())
        assertFalse(longValue.isDouble())
        assertFalse(falseValue.isDouble())
        assertFalse(trueValue.isDouble())
        assertFalse(listValue.isDouble())
        assertFalse(bundleValue.isDouble())
    }

    @Test
    fun isLong_ReturnsTrue_WhenValueIsLong() {
        assertTrue(longValue.isLong())
    }

    @Test
    fun isLong_ReturnsFalse_WhenValueIsNotLong() {
        assertFalse(stringValue.isLong())
        assertFalse(intValue.isLong())
        assertFalse(doubleValue.isLong())
        assertFalse(falseValue.isLong())
        assertFalse(trueValue.isLong())
        assertFalse(listValue.isLong())
        assertFalse(bundleValue.isLong())
    }

    @Test
    fun isBoolean_ReturnsTrue_WhenValueIsBoolean() {
        assertTrue(falseValue.isBoolean())
        assertTrue(trueValue.isBoolean())
    }

    @Test
    fun isBoolean_ReturnsFalse_WhenValueIsNotBoolean() {
        assertFalse(stringValue.isBoolean())
        assertFalse(intValue.isBoolean())
        assertFalse(doubleValue.isBoolean())
        assertFalse(longValue.isBoolean())
        assertFalse(listValue.isBoolean())
        assertFalse(bundleValue.isBoolean())
    }

    @Test
    fun isNumber_ReturnsTrue_WhenValueIsNumber() {
        assertTrue(intValue.isNumber())
        assertTrue(doubleValue.isNumber())
        assertTrue(longValue.isNumber())
    }

    @Test
    fun isNumber_ReturnsFalse_WhenValueIsNotNumber() {
        assertFalse(stringValue.isNumber())
        assertFalse(falseValue.isNumber())
        assertFalse(trueValue.isNumber())
        assertFalse(listValue.isNumber())
        assertFalse(bundleValue.isNumber())
    }


    @Test
    fun getBundle_ReturnsBundle_WhenValueIsBundle() {
        assertNotNull(bundleValue.getBundle())
        assertEquals(bundle, bundleValue.getBundle())
    }

    @Test
    fun getBundle_ReturnsNull_WhenValueIsNotBundle() {
        assertNull(stringValue.getBundle())
        assertNull(intValue.getBundle())
        assertNull(doubleValue.getBundle())
        assertNull(longValue.getBundle())
        assertNull(falseValue.getBundle())
        assertNull(trueValue.getBundle())
        assertNull(listValue.getBundle())
    }

    @Test
    fun getList_ReturnsList_WhenValueIsList() {
        assertNotNull(listValue.getList())
        assertEquals(list, listValue.getList())
    }

    @Test
    fun getList_ReturnsNull_WhenValueIsNotList() {
        assertNull(stringValue.getList())
        assertNull(intValue.getList())
        assertNull(doubleValue.getList())
        assertNull(longValue.getList())
        assertNull(falseValue.getList())
        assertNull(trueValue.getList())
        assertNull(bundleValue.getList())
    }

    @Test
    fun getString_ReturnsString_WhenValueIsString() {
        assertNotNull(stringValue.getString())
        assertEquals(string, stringValue.getString())
    }

    @Test
    fun isString_ReturnsNull_WhenValueIsNotString() {
        assertNull(intValue.getString())
        assertNull(doubleValue.getString())
        assertNull(longValue.getString())
        assertNull(falseValue.getString())
        assertNull(trueValue.getString())
        assertNull(listValue.getString())
        assertNull(bundleValue.getString())
    }

    @Test
    fun getInt_ReturnsInt_WhenValueIsNumber() {
        assertNotNull(intValue.getInt())
        assertNotNull(doubleValue.getInt())
        assertNotNull(longValue.getInt())

        assertEquals(int, intValue.getInt())
        assertEquals(double.toInt(), doubleValue.getInt())
        assertEquals(long.toInt(), longValue.getInt())
    }

    @Test
    fun getInt_ReturnsNull_WhenValueIsNotNumber() {
        assertNull(stringValue.getInt())
        assertNull(falseValue.getInt())
        assertNull(trueValue.getInt())
        assertNull(listValue.getInt())
        assertNull(bundleValue.getInt())
    }

    @Test
    fun getDouble_ReturnsDouble_WhenValueIsNumber() {
        assertNotNull(intValue.getDouble())
        assertNotNull(doubleValue.getDouble())
        assertNotNull(longValue.getDouble())

        assertEquals(int.toDouble(), intValue.getDouble())
        assertEquals(double, doubleValue.getDouble())
        assertEquals(long.toDouble(), longValue.getDouble())
    }

    @Test
    fun getDouble_ReturnsNull_WhenValueIsNotDouble() {
        assertNull(stringValue.getDouble())
        assertNull(falseValue.getDouble())
        assertNull(trueValue.getDouble())
        assertNull(listValue.getDouble())
        assertNull(bundleValue.getDouble())
    }

    @Test
    fun getLong_ReturnsLong_WhenValueIsLong() {
        assertNotNull(intValue.getLong())
        assertNotNull(doubleValue.getLong())
        assertNotNull(longValue.getLong())

        assertEquals(int.toLong(), intValue.getLong())
        assertEquals(double.toLong(), doubleValue.getLong())
        assertEquals(long, longValue.getLong())
    }

    @Test
    fun getLong_ReturnsNull_WhenValueIsNotLong() {
        assertNull(stringValue.getLong())
        assertNull(falseValue.getLong())
        assertNull(trueValue.getLong())
        assertNull(listValue.getLong())
        assertNull(bundleValue.getLong())
    }

    @Test
    fun getBoolean_ReturnsBoolean_WhenValueIsBoolean() {
        assertFalse(falseValue.getBoolean()!!)
        assertTrue(trueValue.getBoolean()!!)
    }

    @Test
    fun getBoolean_ReturnsNull_WhenValueIsNotBoolean() {
        assertNull(stringValue.getBoolean())
        assertNull(intValue.getBoolean())
        assertNull(doubleValue.getBoolean())
        assertNull(longValue.getBoolean())
        assertNull(listValue.getBoolean())
        assertNull(bundleValue.getBoolean())
    }


    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualBundle() {
        assertTrue(bundleValue == bundleValue)

        val copy = bundle.copy().asTealiumValue()
        assertTrue(bundleValue == copy)
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotBundle() {
        assertFalse(bundleValue == stringValue)
        assertFalse(bundleValue == intValue)
        assertFalse(bundleValue == doubleValue)
        assertFalse(bundleValue == longValue)
        assertFalse(bundleValue == falseValue)
        assertFalse(bundleValue == trueValue)
        assertFalse(bundleValue == listValue)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualList() {
        assertTrue(listValue == listValue)

        val copy = list.copy().asTealiumValue()
        assertTrue(listValue == copy)
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotList() {
        assertFalse(listValue == stringValue)
        assertFalse(listValue == intValue)
        assertFalse(listValue == doubleValue)
        assertFalse(listValue == longValue)
        assertFalse(listValue == falseValue)
        assertFalse(listValue == trueValue)
        assertFalse(listValue == bundleValue)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualString() {
        assertTrue(stringValue == stringValue)

        assertTrue(stringValue == TealiumValue.string(string))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotString() {
        assertFalse(stringValue == intValue)
        assertFalse(stringValue == doubleValue)
        assertFalse(stringValue == longValue)
        assertFalse(stringValue == falseValue)
        assertFalse(stringValue == trueValue)
        assertFalse(stringValue == listValue)
        assertFalse(stringValue == bundleValue)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualBoolean() {
        assertTrue(trueValue == trueValue)
        assertTrue(falseValue == falseValue)

        assertTrue(trueValue == TealiumValue.boolean(true))
        assertTrue(falseValue == TealiumValue.boolean(false))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotEqualBoolean() {
        assertFalse(trueValue == stringValue)
        assertFalse(trueValue == intValue)
        assertFalse(trueValue == doubleValue)
        assertFalse(trueValue == longValue)
        assertFalse(trueValue == falseValue)
        assertFalse(trueValue == listValue)
        assertFalse(trueValue == bundleValue)

        assertFalse(falseValue == stringValue)
        assertFalse(falseValue == intValue)
        assertFalse(falseValue == doubleValue)
        assertFalse(falseValue == longValue)
        assertFalse(falseValue == trueValue)
        assertFalse(falseValue == listValue)
        assertFalse(falseValue == bundleValue)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualDouble() {
        assertTrue(doubleValue == doubleValue)

        assertTrue(doubleValue == TealiumValue.double(double))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotEqualDouble() {
        assertFalse(doubleValue == stringValue)
        assertFalse(doubleValue == intValue)
        assertFalse(doubleValue == longValue)
        assertFalse(doubleValue == falseValue)
        assertFalse(doubleValue == trueValue)
        assertFalse(doubleValue == listValue)
        assertFalse(doubleValue == bundleValue)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualInt() {
        assertTrue(intValue == intValue)

        assertTrue(intValue == TealiumValue.int(int))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotEqualInt() {
        assertFalse(intValue == stringValue)
        assertFalse(intValue == longValue)
        assertFalse(intValue == doubleValue)
        assertFalse(intValue == falseValue)
        assertFalse(intValue == trueValue)
        assertFalse(intValue == listValue)
        assertFalse(intValue == bundleValue)
    }

    @Test
    fun equals_ReturnsTrue_WhenOtherIsEqualLong() {
        assertTrue(longValue == longValue)

        assertTrue(longValue == TealiumValue.long(long))
    }

    @Test
    fun equals_ReturnsFalse_WhenValueIsNotEqualLong() {
        assertFalse(longValue == stringValue)
        assertFalse(longValue == intValue)
        assertFalse(longValue == doubleValue)
        assertFalse(longValue == falseValue)
        assertFalse(longValue == trueValue)
        assertFalse(longValue == listValue)
        assertFalse(longValue == bundleValue)
    }

    @Test
    fun equals_ReturnsTrue_WhenNumericValuesAreEqual() {
        assertTrue(TealiumValue.long(1L) == TealiumValue.int(1))
        assertTrue(TealiumValue.int(1) == TealiumValue.long(1L))

        assertTrue(TealiumValue.double(10.0) == TealiumValue.int(10))
        assertTrue(TealiumValue.double(10.0) == TealiumValue.long(10))
        assertTrue(TealiumValue.int(10) == TealiumValue.double(10.0))
        assertTrue(TealiumValue.long(10) == TealiumValue.double(10.0))
    }

    @Test
    fun convert_Null_Returns_TealiumValueNull() {
        val value = TealiumValue.convert(null)

        assertEquals(TealiumValue.NULL, value)
    }

    @Test
    fun convert_TealiumValueNull_Returns_TealiumValueNull() {
        val value = TealiumValue.convert(TealiumValue.NULL)

        assertEquals(TealiumValue.NULL, value)
    }

    @Test
    fun convert_Int_Returns_TealiumValueInt() {
        val value = TealiumValue.convert(10)

        assertTrue(value.isInt())
        assertEquals(10, value.value)
    }

    @Test
    fun convert_Long_Returns_TealiumValueLong() {
        val value = TealiumValue.convert(100L)

        assertTrue(value.isLong())
        assertEquals(100L, value.value)
    }

    @Test
    fun convert_Double_Returns_TealiumValueDouble() {
        val value = TealiumValue.convert(100.111)

        assertTrue(value.isDouble())
        assertEquals(100.111, value.value)
    }

    @Test
    fun convert_Boolean_Returns_TealiumValueBoolean() {
        val value = TealiumValue.convert(false)

        assertTrue(value.isBoolean())
        assertEquals(false, value.value)
    }

    @Test
    fun convert_TealiumList_Returns_TealiumValueList() {
        val value = TealiumValue.convert(TealiumList.create {
            add(1)
            add(2)
            add(3)
        })

        assertTrue(value.isList())
        assertEquals(TealiumList.create {
            add(1)
            add(2)
            add(3)
        }, value.getList())
    }

    @Test
    fun convert_Bundle_Returns_TealiumValueBundle() {
        val value = TealiumValue.convert(TealiumBundle.create {
            put("key", "value")
        })

        assertTrue(value.isBundle())
        assertEquals("value", value.getBundle()!!.getString("key"))
    }

    @Test
    fun convert_Collection_Returns_TealiumValueList() {
        val value = TealiumValue.convert(listOf(1, 2, 3))

        assertTrue(value.isList())
        assertEquals(TealiumList.create {
            add(1)
            add(2)
            add(3)
        }, value.value)
    }

    @Test
    fun convert_Array_Returns_TealiumValueList() {
        val value = TealiumValue.convert(arrayOf(1, 2, 3))

        assertTrue(value.isList())
        assertEquals(TealiumList.create {
            add(1)
            add(2)
            add(3)
        }, value.value)
    }

    @Test
    fun convert_Map_Returns_TealiumValueBundle() {
        val value = TealiumValue.convert(
            mapOf(
                "string" to "value",
                "int" to 10,
                "long" to 100L,
                "double" to 111.111,
                "boolean" to true
            )
        )

        val valueBundle = value.getBundle()!!

        assertTrue(value.isBundle())
        assertEquals("value", valueBundle.getString("string"))
        assertEquals(10, valueBundle.getInt("int"))
        assertEquals(100L, valueBundle.getLong("long"))
        assertEquals(111.111, valueBundle.getDouble("double"))
        assertEquals(true, valueBundle.getBoolean("boolean"))
    }

    @Test
    fun convert_JSONArray_Returns_TealiumValueList() {
        val value = TealiumValue.convert(JSONArray().apply {
            put("value")
            put(10)
            put(100L)
            put(111.111)
            put(true)
            put(JSONArray().apply {
                put(1)
            })
            put(JSONObject().apply {
                put("string" , "value")
            })
        })

        val valueList = value.getList()!!
        assertTrue(value.isList())
        assertEquals("value", valueList.getString(0))
        assertEquals(10, valueList.getInt(1))
        assertEquals(100L, valueList.getLong(2))
        assertEquals(111.111, valueList.getDouble(3))
        assertEquals(true, valueList.getBoolean(4))
        assertEquals(1, valueList.getList(5)!!.getInt(0))
        assertEquals("value", valueList.getBundle(6)!!.getString("key"))
    }

    @Test
    fun convert_JSONObject_Returns_TealiumValueBundle() {
        val value = TealiumValue.convert(
            JSONObject().apply {
                put("string", "value")
                put("int", 10)
                put("long", 100L)
                put("double", 111.111)
                put("boolean", true)
                put("array", JSONArray().apply {
                    put(1)
                })
                put("object", JSONObject().apply {
                    put("string" , "value")
                })
            }
        )

        val valueBundle = value.getBundle()!!

        assertTrue(value.isBundle())
        assertEquals("value", valueBundle.getString("string"))
        assertEquals(10, valueBundle.getInt("int"))
        assertEquals(100L, valueBundle.getLong("long"))
        assertEquals(111.111, valueBundle.getDouble("double"))
        assertEquals(true, valueBundle.getBoolean("boolean"))

        assertEquals(1, valueBundle.getList("array")!!.getInt(0))
        assertEquals("value", valueBundle.getBundle("object")!!.getString("key"))
    }



}
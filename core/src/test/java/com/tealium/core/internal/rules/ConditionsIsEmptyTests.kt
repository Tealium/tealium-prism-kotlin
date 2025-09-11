package com.tealium.core.internal.rules

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Condition
import com.tealium.core.api.rules.ConditionEvaluationException
import com.tealium.core.api.rules.MissingDataItemException
import com.tealium.tests.common.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConditionsIsEmptyTests {

    private val list = DataList.create { add("a"); add("b"); add("c") }
    private val payload = DataObject.create {
        put("string", "Value")
        put("int", 345)
        put("double", 3.14)
        put("bool", true)
        put("list", list)
        put("object", DataObject.Builder().put("key", "Value").build())
    }
    private val payloadOfEmpties = DataObject.create {
        put("string", "")
        put("null", DataItem.NULL)
        put("list", DataList.EMPTY_LIST)
        put("object", DataObject.EMPTY_OBJECT)
    }

    @Test
    fun isNotEmpty_Matches_Non_Empty_Keys() {
        payload.forEach { (key, _) ->
            val condition = Condition.isNotEmpty(
                variable = key,
            )
            assertTrue(condition.matches(payload))
        }
    }

    @Test
    fun isNotEmpty_Does_Not_Match_Empty_Keys() {
        payloadOfEmpties.forEach { (key, _) ->
            val condition = Condition.isNotEmpty(
                variable = key,
            )
            assertFalse(condition.matches(payloadOfEmpties))
        }
    }

    @Test
    fun isNotEmpty_Throws_When_DataItem_Missing() {
        val condition = Condition.isNotEmpty(
            variable = "missing",
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }

    @Test
    fun isEmpty_Does_Not_Match_Non_Empty_Keys() {
        payload.forEach { (key, _) ->
            val condition = Condition.isEmpty(
                variable = key,
            )
            assertFalse(condition.matches(payload))
        }
    }

    @Test
    fun isEmpty_Matches_Empty_Keys() {
        payloadOfEmpties.forEach { (key, _) ->
            val condition = Condition.isEmpty(
                variable = key,
            )
            assertTrue(condition.matches(payloadOfEmpties))
        }
    }

    @Test
    fun isEmpty_Throws_When_DataItem_Missing() {
        val condition = Condition.isEmpty(
            variable = "missing",
        )
        assertThrows<ConditionEvaluationException>(cause = MissingDataItemException::class) {
            condition.matches(payload)
        }
    }
}
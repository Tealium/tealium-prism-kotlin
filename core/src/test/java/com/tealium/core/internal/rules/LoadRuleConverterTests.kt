package com.tealium.core.internal.rules

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Condition.Companion.isEqual
import com.tealium.core.api.rules.Rule
import com.tealium.core.internal.rules.LoadRule.Converter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadRuleConverterTests {

    @Test
    fun convert_Returns_Valid_Load_Rule_When_Both_Id_And_Conditions_Present() {
        val conditions = Rule.any(
            Rule.just(isEqual(true, null, "key", "value"))
        )
        val ruleItem = DataObject.create {
            put(Converter.KEY_ID, "rule-1")
            put(Converter.KEY_CONDITIONS, conditions)
        }.asDataItem()

        val rule = Converter.convert(ruleItem)!!

        assertEquals("rule-1", rule.id)
        assertEquals(conditions, rule.conditions)
    }

    @Test
    fun convert_Returns_Null_When_Input_Is_Not_DataObject() {
        assertNull(Converter.convert(DataList.EMPTY_LIST.asDataItem()))
        assertNull(Converter.convert(DataItem.string("")))
    }

    @Test
    fun convert_Returns_Null_When_Id_Is_Not_String() {
        assertNull(Converter.convert(DataObject.EMPTY_OBJECT.asDataItem()))
        assertNull(Converter.convert(DataObject.create {
            put(Converter.KEY_ID, 10)
        }.asDataItem()))
        assertNull(Converter.convert(DataObject.create {
            put(Converter.KEY_ID, true)
        }.asDataItem()))
    }

    @Test
    fun convert_Converts_Condition_With_And() {
        val conditions = Rule.all(
            Rule.just(isEqual(true, null, "key", "value"))
        )
        val ruleItem = DataObject.create {
            put(Converter.KEY_ID, "rule-1")
            put(Converter.KEY_CONDITIONS, conditions)
        }.asDataItem()

        val rule = Converter.convert(ruleItem)!!

        assertEquals("rule-1", rule.id)
        assertEquals(conditions, rule.conditions)
    }

    @Test
    fun convert_Converts_Condition_With_Or() {
        val conditions = Rule.any(
            Rule.just(isEqual(true, null, "key", "value"))
        )
        val ruleItem = DataObject.create {
            put(Converter.KEY_ID, "rule-1")
            put(Converter.KEY_CONDITIONS, conditions)
        }.asDataItem()

        val rule = Converter.convert(ruleItem)!!

        assertEquals("rule-1", rule.id)
        assertEquals(conditions, rule.conditions)
    }

    @Test
    fun convert_Converts_Condition_With_Not() {
        val conditions = Rule.not(
            Rule.just(isEqual(true, null, "key", "value"))
        )
        val ruleItem = DataObject.create {
            put(Converter.KEY_ID, "rule-1")
            put(Converter.KEY_CONDITIONS, conditions)
        }.asDataItem()

        val rule = Converter.convert(ruleItem)!!

        assertEquals("rule-1", rule.id)
        assertEquals(conditions, rule.conditions)
    }

    @Test
    fun convert_Converts_Condition_With_Just() {
        val conditions = Rule.just(
            isEqual(true, null, "key", "value")
        )
        val ruleItem = DataObject.create {
            put(Converter.KEY_ID, "rule-1")
            put(Converter.KEY_CONDITIONS, conditions)
        }.asDataItem()

        val rule = Converter.convert(ruleItem)!!

        assertEquals("rule-1", rule.id)
        assertEquals(conditions, rule.conditions)
    }
}
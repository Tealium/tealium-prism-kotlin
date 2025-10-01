package com.tealium.prism.core.api.rules

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.UnsupportedDataItemException
import com.tealium.prism.core.api.rules.Condition.Companion.isEqual
import com.tealium.prism.core.api.rules.Condition.Companion.isGreaterThan
import com.tealium.prism.core.api.rules.Condition.Companion.isLessThan
import com.tealium.prism.core.internal.rules.conditionConverter
import com.tealium.tests.common.trimJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuleConverterTests {

    val converter = conditionConverter

    @Test
    fun converter_Converts_Single_Condition() {
        val item = DataObject.fromString(
            """
            {
                    "variable": "true",
                    "operator": "equals",
                    "filter" : "true"
            }
        """
        )!!.asDataItem()

        val rule = converter.convert(item)!!

        assertEquals(
            Rule.just(isEqual(false, null, "true", "true")),
            rule
        )
    }

    @Test
    fun converter_Converts_And_Rule_With_Conditions() {
        val item = DataObject.fromString(
            """
            {
                "operator": "and",
                "children": [{
                    "variable": "true",
                    "operator": "equals",
                    "filter" : "true"
                },{
                    "variable": "false",
                    "operator": "equals",
                    "filter" : "false"
                }]
            }
        """
        )!!.asDataItem()

        val rule = converter.convert(item)!!
        assertEquals(
            Rule.all(
                Rule.just(isEqual(false, null, "true", "true")),
                Rule.just(isEqual(false, null, "false", "false"))
            ),
            rule
        )
    }

    @Test
    fun converter_Converts_Or_Rule_With_Conditions() {
        val item = DataObject.fromString(
            """
            {
                "operator": "or",
                "children": [{
                    "variable": "true",
                    "operator": "equals",
                    "filter" : "true"
                },{
                    "variable": "false",
                    "operator": "equals",
                    "filter" : "false"
                }]
            }
        """
        )!!.asDataItem()

        val rule = converter.convert(item)!!

        assertEquals(
            Rule.any(
                Rule.just(isEqual(false, null, "true", "true")),
                Rule.just(isEqual(false, null, "false", "false"))
            ),
            rule
        )
    }

    @Test
    fun converter_Converts_And_With_Nested_Or_Conditions() {
        val item = DataObject.fromString(
            """
            {
                "operator": "and",
                "children": [{
                    "variable": "true",
                    "operator": "equals",
                    "filter" : "true"
                },{
                    "operator": "or",
                    "children": [{
                        "variable": "number",
                        "operator": "greater_than",
                        "filter" : "10"
                    },{
                        "variable": "number",
                        "operator": "less_than",
                        "filter" : "0"
                    }]    
                }]
            }
        """
        )!!.asDataItem()

        val rule = converter.convert(item)!!

        assertEquals(
            Rule.all(
                Rule.just(isEqual(false, null, "true", "true")),
                Rule.any(
                    Rule.just(isGreaterThan(false, null, "number", "10")),
                    Rule.just(isLessThan(false, null, "number", "0"))
                )
            ),
            rule,
        )
    }

    @Test
    fun converter_Converts_Or_With_Nested_And_Conditions() {
        val item = DataObject.fromString(
            """
            {
                "operator": "or",
                "children": [{
                    "variable": "true",
                    "operator": "equals",
                    "filter" : "true"
                },{
                    "operator": "and",
                    "children": [{
                        "variable": "number",
                        "operator": "greater_than",
                        "filter" : "0"
                    },{
                        "variable": "false",
                        "operator": "equals",
                        "filter" : "false"
                    }]    
                }]
            }
        """
        )!!.asDataItem()

        val rule = converter.convert(item)!!

        assertEquals(
            Rule.any(
                Rule.just(isEqual(false, null, "true", "true")),
                Rule.all(
                    Rule.just(isGreaterThan(false, null, "number", "0")),
                    Rule.just(isEqual(false, null, "false", "false"))
                )
            ),
            rule
        )
    }

    @Test
    fun converter_Converts_Not_With_Nested_Conditions() {
        val item = DataObject.fromString(
            """
            {
                "operator": "not",
                "children": [{
                    "variable": "true",
                    "operator": "equals",
                    "filter" : "true"
                }]
            }
        """
        )!!.asDataItem()

        val rule = converter.convert(item)!!

        assertEquals(
            Rule.not(
                Rule.just(isEqual(false, null, "true", "true"))
            ),
            rule
        )
    }

    @Test
    fun asDataItem_Serializes_Rule_Using_Condition_Converter() {
        val rule = Rule.not(
            Rule.just(isEqual(false, null, "true", "true"))
        ).asDataItem()

        assertEquals(
            """
                {
                    "operator": "not",
                    "children": [{
                        "variable": "true",
                        "operator": "equals",
                        "filter": "true"
                    }]
                }
            """.trimJson(),
            rule.toString()
        )
    }

    @Test
    fun asDataItem_Serializes_Rule_Using_DataItem_Converter() {
        val rule = Rule.all(
            Rule.just("rule1"),
            Rule.just("rule2")
        ).asDataItem()

        assertEquals(
            """
                {
                    "operator": "and",
                    "children": [
                        "rule1",
                        "rule2"
                    ]
                }
            """.trimJson(),
            rule.toString()
        )
    }

    @Test(expected = UnsupportedDataItemException::class)
    fun asDataItem_Throws_When_Type_Is_Not_DataItem_Supported() {
        Rule.not(
            Rule.just(Any())
        ).asDataItem()
    }
}
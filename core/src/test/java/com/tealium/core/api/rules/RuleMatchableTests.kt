package com.tealium.core.api.rules

import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Condition.Companion.isDefined
import com.tealium.core.api.rules.Condition.Companion.isEqual
import com.tealium.core.api.rules.Condition.Companion.isGreaterThan
import com.tealium.core.api.rules.Condition.Companion.isLessThan
import com.tealium.core.api.rules.Condition.Companion.isPopulated
import com.tealium.core.api.rules.Condition.Companion.startsWith
import com.tealium.core.api.rules.Rule.Companion.any
import com.tealium.core.api.rules.Rule.Companion.just
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RuleMatchableTests {

    @Test
    fun matches_Returns_Following_Single_Condition() {
        val rule = Rule.just(isEqual(false, null, "true", "true"))

        assertTrue(rule.matches(DataObject.create {
            put("true", true)
        }))
        assertFalse(rule.matches(DataObject.create {
            put("true", false)
        }))
    }

    @Test
    fun matches_Returns_Following_And_Conditions() {
        val rule = Rule.all(
            Rule.just(isEqual(false, null, "true", "true")),
            Rule.just(isEqual(false, null, "false", "false"))
        )

        assertTrue(rule.matches(DataObject.create {
            put("true", true)
            put("false", false)
        }))
        assertFalse(rule.matches(DataObject.create {
            put("true", false)
            put("false", false)
        }))
    }

    @Test
    fun matches_Returns_Following_Or_Conditions() {
        val rule = Rule.any(
            Rule.just(isEqual(false, null, "true", "true")),
            Rule.just(isEqual(false, null, "false", "false"))
        )

        assertTrue(rule.matches(DataObject.create {
            put("true", true)
            put("false", false)
        }))
        assertTrue(rule.matches(DataObject.create {
            put("true", true)
            put("false", true)
        }))
        assertTrue(rule.matches(DataObject.create {
            put("true", false)
            put("false", false)
        }))
        assertFalse(rule.matches(DataObject.create {
            put("true", false)
            put("false", true)
        }))
    }

    @Test
    fun matches_Returns_Following_And_And_Nested_Or_Conditions() {
        val rule = Rule.all(
            Rule.just(isEqual(false, null, "true", "true")),
            Rule.any(
                Rule.just(isGreaterThan(false, null, "number", "10")),
                Rule.just(isLessThan(false, null, "number", "0"))
            )
        )
        assertTrue(rule.matches(DataObject.create {
            put("true", true)
            put("number", 11)
        }))
        assertTrue(rule.matches(DataObject.create {
            put("true", true)
            put("number", -1)
        }))
        assertTrue(rule.matches(DataObject.create {
            put("true", true)
            put("number", 100)
        }))

        IntRange(0, 10).forEach {
            assertFalse(rule.matches(DataObject.create {
                put("true", true)
                put("number", it)
            }))
        }
    }

    private val complexRule: Rule<Condition> = Rule.all(
        // "string" key need to be non-empty
        just(isPopulated(null, "populated")),
        // "true" key should be `true`
        just(isEqual(false, null, "true", "true")),
        Rule.all( // must contain `is_defined` and obj-1.key must start with "prefix"
            just(isDefined(null, "is_defined")),
            just(startsWith(true, listOf("obj-1"), "prefix", "PrEFiX")),
            any( // either `obj-1.list-1` or `obj-1.list-2` need to be non-empty
                just(isPopulated(listOf("obj-1"), "list-1")),
                just(isPopulated(listOf("obj-1"), "list-2"))
            )
        ),
        Rule.not( // must be 1-9 inclusive
            any(
                just(isGreaterThan(true, listOf("obj-1", "obj-2"), "number", "10")),
                just(isLessThan(true, listOf("obj-1", "obj-2"), "number", "0"))
            )
        )
    )

    private val successPayload = DataObject.create {
        put("populated", "populated")
        put("true", true)
        put("is_defined", 10)
        put("obj-1", DataObject.create {
            put("prefix", "prefixed-string")
            put("list-1", DataList.create {
                add("1")
            })
            put("list-2", DataList.create {
                add(true)
            })
            put("obj-2", DataObject.create {
                put("number", 1)
            })
        })
    }

    @Test
    fun matches_Returns_True_When_All_Conditions_Match() {
        assertTrue(complexRule.matches(successPayload))
    }

    @Test
    fun matches_Returns_False_When_Key_Not_Populated() {
        assertFalse(complexRule.matches(successPayload.copy {
            remove("populated")
        }))
        assertFalse(complexRule.matches(successPayload.copy {
            put("populated", "")
        }))
    }

    @Test
    fun matches_Returns_False_When_Key_Not_Equals() {
        assertFalse(complexRule.matches(successPayload.copy {
            remove("true")
        }))
        assertFalse(complexRule.matches(successPayload.copy {
            put("true", false)
        }))
    }

    @Test
    fun matches_Returns_False_When_Nested_And_Rules_Do_Not_All_Match() {
        assertFalse(complexRule.matches(successPayload.copy {
            remove("is_defined")
        }))
        val obj1 = successPayload.getDataObject("obj-1")!!
        assertFalse(complexRule.matches(successPayload.copy {
            put("obj-1", obj1.copy {
                put("prefix", "not-prefixed")
            })
        }))
        assertFalse(complexRule.matches(successPayload.copy {
            put("obj-1", obj1.copy {
                remove("list-1")
                remove("list-2")
            })
        }))
    }

    @Test
    fun matches_Returns_False_When_Either_Nested_Any_Rules_Match() {
        val obj1 = successPayload.getDataObject("obj-1")!!
        assertTrue(complexRule.matches(successPayload.copy {
            put("obj-1", obj1.copy {
                remove("list-1")
            })
        }))
        assertTrue(complexRule.matches(successPayload.copy {
            put("obj-1", obj1.copy {
                remove("list-2")
            })
        }))
    }

    @Test
    fun matches_Returns_False_When_Not_Any_Nested_Rules_Match() {
        val obj1 = successPayload.getDataObject("obj-1")!!
        val obj2 = obj1.getDataObject("obj-2")!!
        assertFalse(complexRule.matches(successPayload.copy {
            put("obj-1", obj1.copy {
                put("obj-2", obj2.copy {
                    put("number", 0)
                })
            })
        }))
        assertFalse(complexRule.matches(successPayload.copy {
            put("obj-1", obj1.copy {
                put("obj-2", obj2.copy {
                    put("number", 10)
                })
            })
        }))
    }
}

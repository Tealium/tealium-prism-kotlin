package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Condition.Companion.isEqual
import com.tealium.core.api.rules.Rule
import com.tealium.core.internal.rules.LoadRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkSettingsTests {

    // TODO - deserialization tests for other objects (Modules/CoreSettings)

    @Test
    fun init_With_LoadRules_Returns_SdkSettings_With_LoadRules() {
        val conditions = Rule.all(Rule.just(isEqual(true, null, "key", "value")))
        val settingsObject = DataObject.create {
            put(SdkSettings.KEY_LOAD_RULES, DataObject.create {
                put("rule-1", DataObject.create {
                    put(LoadRule.Converter.KEY_ID, "rule-1")
                    put(LoadRule.Converter.KEY_CONDITIONS, conditions)
                })
            })
        }

        val settings = SdkSettings.fromDataObject(settingsObject)

        val rule = settings.loadRules["rule-1"]!!
        assertEquals("rule-1", rule.id)
        assertEquals(conditions, rule.conditions)
    }

    @Test
    fun init_With_LoadRules_Returns_SdkSettings_Without_Invalid_LoadRules() {
        val settingsObject = DataObject.create {
            put(SdkSettings.KEY_LOAD_RULES, DataObject.create {
                put("rule-1", DataObject.create {
                    // missing id
                })
            })
        }

        val settings = SdkSettings.fromDataObject(settingsObject)

        assertTrue(settings.loadRules.isEmpty())
    }

    @Test
    fun init_With_LoadRules_Returns_SdkSettings_Without_Null_Conditions_When_Omitted() {
        val settingsObject = DataObject.create {
            put(SdkSettings.KEY_LOAD_RULES, DataObject.create {
                put("rule-1", DataObject.create {
                    put(LoadRule.Converter.KEY_ID, "rule-1")
                    // missing conditions
                })
            })
        }

        val settings = SdkSettings.fromDataObject(settingsObject)

        val rule = settings.loadRules["rule-1"]
        assertNull(rule)
    }
}
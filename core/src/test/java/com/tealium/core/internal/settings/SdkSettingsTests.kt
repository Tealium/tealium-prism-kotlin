package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Condition.Companion.isEqual
import com.tealium.core.api.rules.Rule
import com.tealium.core.api.transform.TransformationScope
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.internal.misc.Converters.TransformationSettingsConverter.KEY_CONFIGURATION
import com.tealium.core.internal.misc.Converters.TransformationSettingsConverter.KEY_SCOPES
import com.tealium.core.internal.misc.Converters.TransformationSettingsConverter.KEY_TRANSFORMATION_ID
import com.tealium.core.internal.misc.Converters.TransformationSettingsConverter.KEY_TRANSFORMER_ID
import com.tealium.core.internal.rules.LoadRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun init_With_Transformations_Returns_SdkSettings_With_Transformations() {
        val expected = TransformationSettings(
            "id",
            "transformer",
            setOf(TransformationScope.AllDispatchers),
            DataObject.create { put("key", "value") }
        )
        val settingsObject = DataObject.create {
            put(SdkSettings.KEY_TRANSFORMATIONS, DataObject.create {
                put("transformer-id", DataObject.create {
                    put(KEY_TRANSFORMATION_ID, "id")
                    put(KEY_TRANSFORMER_ID, "transformer")
                    put(
                        KEY_SCOPES,
                        DataList.create { add(TransformationScope.AllDispatchers.value) })
                    put(KEY_CONFIGURATION, DataObject.create { put("key", "value") })
                })
            })
        }

        val settings = SdkSettings.fromDataObject(settingsObject)

        val recreatedTransformation = settings.transformations["transformer-id"]!!
        assertEquals(expected, recreatedTransformation)
    }

    @Test
    fun init_With_Transformations_Returns_SdkSettings_Without_Invalid_Transformations() {
        val validTransformation = DataObject.create {
            put(KEY_TRANSFORMATION_ID, "id")
            put(KEY_TRANSFORMER_ID, "transformer")
            put(KEY_SCOPES, DataList.create { add(TransformationScope.AllDispatchers.value) })
        }
        val settingsObject = DataObject.create {
            put(SdkSettings.KEY_TRANSFORMATIONS, DataObject.create {
                put("missing-id", validTransformation.copy { remove(KEY_TRANSFORMATION_ID) })
                put("missing-transformer", validTransformation.copy { remove(KEY_TRANSFORMER_ID) })
                put("missing-scopes", validTransformation.copy { remove(KEY_SCOPES) })
            })
        }

        val settings = SdkSettings.fromDataObject(settingsObject)

        assertTrue(settings.transformations.isEmpty())
    }
}
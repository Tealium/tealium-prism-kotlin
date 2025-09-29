package com.tealium.core.api.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Rule
import com.tealium.core.internal.settings.ModuleSettings
import com.tealium.tests.common.trimJson
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectorSettingsBuilderTests {

    @Test
    fun setRules_Sets_String_Rules_In_DataObject() {
        val settings = CollectorSettingsBuilder("collector")
            .setRules(
                Rule.just("rule_1")
                    .or(Rule.just("rule_2"))
            )
            .build()

        val rules = settings.getDataObject(ModuleSettings.KEY_RULES)!!
        assertEquals(
            DataObject.fromString(
            """
                {
                    "operator": "or",
                    "children": [
                        "rule_1",
                        "rule_2"
                    ]
                }
            """.trimJson()), rules)
    }
}
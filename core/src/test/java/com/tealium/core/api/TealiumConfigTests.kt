package com.tealium.core.api

import com.tealium.core.api.rules.Condition.Companion.isDefined
import com.tealium.core.api.rules.Condition.Companion.isEqual
import com.tealium.core.api.rules.Rule
import com.tealium.core.internal.rules.LoadRule
import com.tealium.core.internal.settings.SdkSettings
import com.tealium.tests.common.getDefaultConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TealiumConfigTests {

    // TODO - Other enforcedSettings tests (Modules/CoreSettings)

    @Test
    fun init_Adds_LoadRules_To_Enforced_Settings_Under_LoadRules_Key() {
        val loadRule1 = LoadRule("rule-1", Rule.just(isEqual(true, null, "key", "value")))
        val loadRule2 = LoadRule("rule-2", Rule.just(isDefined(null, "key")))
        val rules = mapOf(
            loadRule1.id to loadRule1.conditions!!,
            loadRule2.id to loadRule2.conditions!!
        )

        val config = getDefaultConfig(app = mockk(), rules = rules)

        val loadRules = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_LOAD_RULES)!!
        assertEquals(loadRule1, loadRules.get("rule-1", LoadRule.Converter))
        assertEquals(loadRule2, loadRules.get("rule-2", LoadRule.Converter))
    }

    @Test
    fun init_Omits_LoadRules_Key_When_No_Rules_Provided() {
        val config = getDefaultConfig(app = mockk(), rules = null)

        val loadRules = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_LOAD_RULES)
        assertNull(loadRules)
    }
}
package com.tealium.core.api

import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.rules.Condition.Companion.isDefined
import com.tealium.core.api.rules.Condition.Companion.isEqual
import com.tealium.core.api.rules.Rule
import com.tealium.core.api.transform.TransformationScope
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.internal.dispatch.barrier
import com.tealium.core.internal.dispatch.barrierFactory
import com.tealium.core.internal.misc.Converters
import com.tealium.core.internal.rules.LoadRule
import com.tealium.core.internal.settings.BarrierSettings
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

        val config = getDefaultConfig(app = mockk())
        config.addLoadRule(loadRule1.id, loadRule1.conditions)
        config.addLoadRule(loadRule2.id, loadRule2.conditions)

        val loadRules = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_LOAD_RULES)!!
        assertEquals(loadRule1, loadRules.get("rule-1", LoadRule.Converter))
        assertEquals(loadRule2, loadRules.get("rule-2", LoadRule.Converter))
    }

    @Test
    fun init_Adds_Transformations_To_Enforced_Settings_Under_Transformations_Key() {
        val transformation1 = TransformationSettings(
            "id-1",
            "transformer-1",
            setOf(TransformationScope.AfterCollectors),
            configuration = DataObject.create { put("key", "value") }
        )
        val transformation2 = TransformationSettings(
            "id-2",
            "transformer-2",
            setOf(TransformationScope.AllDispatchers),
            configuration = DataObject.create { put("key", "value") }
        )

        val config = getDefaultConfig(app = mockk())
        config.addTransformation(transformation1)
        config.addTransformation(transformation2)

        val transformations = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_TRANSFORMATIONS)!!
        assertEquals(transformation1, transformations.get("transformer-1-id-1", Converters.TransformationSettingsConverter))
        assertEquals(transformation2, transformations.get("transformer-2-id-2", Converters.TransformationSettingsConverter))
    }

    @Test
    fun init_Adds_Barriers_To_Enforced_Settings_Under_Barriers_Key() {
        val barrier = barrier("test-barrier", Observables.just(BarrierState.Open))
        val barrierFactory = barrierFactory(barrier)
        val scopes = setOf(BarrierScope.All, BarrierScope.Dispatcher("dispatcher"))

        val config = getDefaultConfig(app = mockk())
        config.addBarrier(barrierFactory, scopes)

        val barriers = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_BARRIERS)!!
        val barrierSettings = barriers.get(barrier.id, BarrierSettings.Converter)!!
        assertEquals(barrier.id, barrierSettings.barrierId)
        assertEquals(scopes, barrierSettings.scope)
        assertEquals(DataObject.EMPTY_OBJECT, barrierSettings.configuration)
    }

    @Test
    fun init_Does_Not_Add_BarrierSettings_To_Enforced_Settings_When_Scopes_Omitted() {
        val barrier = barrier("test-barrier", Observables.just(BarrierState.Open))
        val barrierFactory = barrierFactory(barrier)

        val config = getDefaultConfig(app = mockk())
        config.addBarrier(barrierFactory)

        val barrierSettings = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_BARRIERS)
            ?.get(barrier.id, BarrierSettings.Converter)
        assertNull(barrierSettings)
    }

    @Test
    fun init_Omits_LoadRules_Key_When_No_Rules_Provided() {
        val config = getDefaultConfig(app = mockk())

        val loadRules = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_LOAD_RULES)
        assertNull(loadRules)
    }

    @Test
    fun init_Omits_Transformations_Key_When_No_Transformations_Provided() {
        val config = getDefaultConfig(app = mockk())

        val transformations = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_TRANSFORMATIONS)
        assertNull(transformations)
    }

    @Test
    fun init_Omits_Barriers_Key_When_No_Barriers_Provided() {
        val config = getDefaultConfig(app = mockk())

        val barriers = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_BARRIERS)
        assertNull(barriers)
    }
}
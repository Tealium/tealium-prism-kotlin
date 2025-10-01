package com.tealium.prism.core.api

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.consent.CmpAdapter
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.rules.Condition.Companion.isDefined
import com.tealium.prism.core.api.rules.Condition.Companion.isEqual
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.settings.ConsentConfigurationBuilder
import com.tealium.prism.core.api.settings.TestSettingsBuilder
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.internal.dispatch.barrier
import com.tealium.prism.core.internal.dispatch.barrierFactory
import com.tealium.prism.core.internal.misc.Converters
import com.tealium.prism.core.internal.rules.LoadRule
import com.tealium.prism.core.internal.settings.BarrierSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.prism.core.internal.settings.consent.ConsentPurpose
import com.tealium.prism.core.internal.settings.consent.ConsentSettings
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        val transformations =
            config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_TRANSFORMATIONS)!!
        assertEquals(
            transformation1,
            transformations.get("transformer-1-id-1", Converters.TransformationSettingsConverter)
        )
        assertEquals(
            transformation2,
            transformations.get("transformer-2-id-2", Converters.TransformationSettingsConverter)
        )
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

        val transformations =
            config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_TRANSFORMATIONS)
        assertNull(transformations)
    }

    @Test
    fun init_Omits_Barriers_Key_When_No_Barriers_Provided() {
        val config = getDefaultConfig(app = mockk())

        val barriers = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_BARRIERS)
        assertNull(barriers)
    }

    @Test
    fun enableConsentIntegration_Adds_Consent_To_Enforced_Settings_Under_Consent_Key_When_Enforced_Settings_Set() {
        val cmpAdapter = mockCmpAdapter()

        val config = getDefaultConfig(app = mockk())
        config.enableConsentIntegration(cmpAdapter) { settings ->
            settings.addPurpose("purpose1", setOf("dispatcher1"))
        }

        val consent = config.enforcedSdkSettings.get(SdkSettings.KEY_CONSENT)
        assertNotNull(consent)
    }

    @Test
    fun enableConsentIntegration_Does_Not_Add_Consent_To_Enforced_Settings_When_Enforced_Settings_Omitted() {
        val cmpAdapter = mockCmpAdapter()

        val config = getDefaultConfig(app = mockk())
        config.enableConsentIntegration(cmpAdapter)

        val consent = config.enforcedSdkSettings.get(SdkSettings.KEY_CONSENT)
        assertNull(consent)
    }

    @Test
    fun enableConsentIntegration_Adds_Correctly_Serialized_Consent_Settings_To_Enforced_Settings() {
        val cmpAdapter = mockCmpAdapter("cmp1")

        val config = getDefaultConfig(app = mockk())
        config.enableConsentIntegration(cmpAdapter) { settings ->
            settings.addPurpose("purpose1", setOf("dispatcher1"))
                .setRefireDispatcherIds(setOf("dispatcher2"))
                .setTealiumPurposeId("tealium_purpose")
        }

        val consent =
            config.enforcedSdkSettings.get(SdkSettings.KEY_CONSENT, ConsentSettings.Converter)!!
        val cmp1 = consent.configurations["cmp1"]!!
        assertEquals("tealium_purpose", cmp1.tealiumPurposeId)
        assertEquals(setOf("dispatcher2"), cmp1.refireDispatcherIds)
        assertEquals(ConsentPurpose("purpose1", setOf("dispatcher1")), cmp1.purposes["purpose1"])
    }

    @Test
    fun enableConsentIntegration_Removes_Previously_Added_Settings_When_Called_Multiple_Times() {
        val cmp1 = mockCmpAdapter("cmp1")
        val cmp2 = mockCmpAdapter("cmp2")
        val settingsBlock: (ConsentConfigurationBuilder) -> ConsentConfigurationBuilder =
            { settings ->
                settings.addPurpose("purpose1", setOf("dispatcher1"))
                    .setRefireDispatcherIds(setOf("dispatcher2"))
                    .setTealiumPurposeId("tealium_purpose")
            }

        val config = getDefaultConfig(app = mockk())
        config.enableConsentIntegration(cmp1, settingsBlock)
        config.enableConsentIntegration(cmp2, settingsBlock)

        val consent =
            config.enforcedSdkSettings.get(SdkSettings.KEY_CONSENT, ConsentSettings.Converter)!!

        assertEquals(1, consent.configurations.size)
        assertNull(consent.configurations["cmp1"])
        assertNotNull(consent.configurations["cmp2"])
    }

    @Test
    fun moduleFactory_Settings_Are_Added_To_Enforced_Settings_Using_ModuleId() {
        val settings1 = TestSettingsBuilder("module_type")
            .setModuleId("module_id_1")
            .build()
        val settings2 = TestSettingsBuilder("module_type")
            .setModuleId("module_id_2")
            .build()
        val factory = TestModuleFactory(
            "module_type",
            config = listOf(settings1, settings2),
            allowsMultipleInstances = true
        )

        val config = getDefaultConfig(app = mockk(), modules = listOf(factory))

        val moduleObject = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_MODULES)!!
        assertEquals(settings1, moduleObject.getDataObject("module_id_1"))
        assertEquals(settings2, moduleObject.getDataObject("module_id_2"))
    }

    @Test
    fun moduleFactory_Settings_Are_Added_To_Enforced_Settings_Using_ModuleType_When_ModuleId_Omitted() {
        val settings1 = TestSettingsBuilder("module_type")
            .build()
        val factory = TestModuleFactory("module_type", config = listOf(settings1))

        val config = getDefaultConfig(app = mockk(), modules = listOf(factory))

        val moduleObject = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_MODULES)!!
        assertEquals(settings1, moduleObject.getDataObject("module_type"))
    }

    @Test
    fun moduleFactory_Settings_Only_Adds_Single_ModuleSettings_When_ModuleIds_Clash() {
        val settings1 = TestSettingsBuilder("module_type")
            .setModuleId("1")
            .build()
        val settings2 = TestSettingsBuilder("module_type")
            .setModuleId("1")
            .build()
        val factory = TestModuleFactory("module_type", config = listOf(settings1, settings2))

        val config = getDefaultConfig(app = mockk(), modules = listOf(factory))

        val moduleObject = config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_MODULES)!!
        assertEquals(settings1, moduleObject.getDataObject("1"))
        assertNull(moduleObject.getDataObject("2"))
    }

    private fun mockCmpAdapter(id: String = "cmp"): CmpAdapter {
        val adapter = mockk<CmpAdapter>(relaxed = true)
        every { adapter.id } returns id
        return adapter
    }
}
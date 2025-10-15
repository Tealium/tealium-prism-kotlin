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
import com.tealium.prism.core.internal.modules.ModuleRegistry
import com.tealium.prism.core.internal.rules.LoadRule
import com.tealium.prism.core.internal.settings.BarrierSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.prism.core.internal.settings.consent.ConsentPurpose
import com.tealium.prism.core.internal.settings.consent.ConsentSettings
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.getDefaultConfigBuilder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TealiumConfigTests {

    // TODO - Other enforcedSettings tests (Modules/CoreSettings)

    @Before
    fun setUp() {
        ModuleRegistry.clearAdditionalModules()
    }

    @Test
    fun init_Adds_LoadRules_To_Enforced_Settings_Under_LoadRules_Key() {
        val loadRule1 = LoadRule("rule-1", Rule.just(isEqual(true, null, "key", "value")))
        val loadRule2 = LoadRule("rule-2", Rule.just(isDefined(null, "key")))

        val config = getDefaultConfigBuilder(app = mockk())
            .addLoadRule(loadRule1.id, loadRule1.conditions)
            .addLoadRule(loadRule2.id, loadRule2.conditions)
            .build()

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

        val config = getDefaultConfigBuilder(app = mockk())
            .addTransformation(transformation1)
            .addTransformation(transformation2)
            .build()

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

        val config = getDefaultConfigBuilder(app = mockk())
            .addBarrier(barrierFactory, scopes)
            .build()

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

        val config = getDefaultConfigBuilder(app = mockk())
            .addBarrier(barrierFactory)
            .build()

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

        val config = getDefaultConfigBuilder(app = mockk())
            .enableConsentIntegration(cmpAdapter) { settings ->
                settings.addPurpose("purpose1", setOf("dispatcher1"))
            }.build()

        val consent = config.enforcedSdkSettings.get(SdkSettings.KEY_CONSENT)
        assertNotNull(consent)
    }

    @Test
    fun enableConsentIntegration_Does_Not_Add_Consent_To_Enforced_Settings_When_Enforced_Settings_Omitted() {
        val cmpAdapter = mockCmpAdapter()

        val config = getDefaultConfigBuilder(app = mockk())
            .enableConsentIntegration(cmpAdapter)
            .build()

        val consent = config.enforcedSdkSettings.get(SdkSettings.KEY_CONSENT)
        assertNull(consent)
    }

    @Test
    fun enableConsentIntegration_Adds_Correctly_Serialized_Consent_Settings_To_Enforced_Settings() {
        val cmpAdapter = mockCmpAdapter("cmp1")

        val config = getDefaultConfigBuilder(app = mockk())
            .enableConsentIntegration(cmpAdapter) { settings ->
                settings.addPurpose("purpose1", setOf("dispatcher1"))
                    .setRefireDispatcherIds(setOf("dispatcher2"))
                    .setTealiumPurposeId("tealium_purpose")
            }.build()

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

        val config = getDefaultConfigBuilder(app = mockk())
            .enableConsentIntegration(cmp1, settingsBlock)
            .enableConsentIntegration(cmp2, settingsBlock)
            .build()

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

    @Test
    fun build_Adds_Default_Modules_To_Modules_List() {
        val config = getDefaultConfig(mockk(), modules = emptyList())

        assertTrue(config.modules.isNotEmpty())
        assertTrue(config.modules.containsAll(Modules.defaultModules))
    }

    @Test
    fun build_Adds_Additional_Default_Modules_To_Modules_List() {
        val additionalModule = TestModuleFactory("additional")
        ModuleRegistry.addDefaultModules(listOf(additionalModule))
        val config = getDefaultConfig(mockk(), modules = emptyList())

        assertTrue(config.modules.contains(additionalModule))
    }

    @Test
    fun build_Deduplicates_By_Module_Type_Preferring_Added_Module() {
        val customDataLayer = Modules.dataLayer { settings ->
            settings.setOrder(1)
        }
        val config = getDefaultConfig(mockk(), modules = listOf(customDataLayer))

        assertTrue(config.modules.contains(customDataLayer))
        assertFalse(config.modules.contains(Modules.defaultModules.find { it.moduleType == Modules.Types.DATA_LAYER }))
    }

    @Test
    fun enforcedSettings_Always_Contains_TealiumData_Module_Type() {
        val config = getDefaultConfig(mockk(), modules = emptyList())

        assertNotNull(
            config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_MODULES)!!
                .getDataObject(Modules.Types.TEALIUM_DATA)
        )
    }

    @Test
    fun enforcedSettings_Always_Contains_DataLayer_Module_Type() {
        val config = getDefaultConfig(mockk(), modules = emptyList())

        assertNotNull(
            config.enforcedSdkSettings.getDataObject(SdkSettings.KEY_MODULES)!!
                .getDataObject(Modules.Types.DATA_LAYER)
        )
    }

    private fun mockCmpAdapter(id: String = "cmp"): CmpAdapter {
        val adapter = mockk<CmpAdapter>(relaxed = true)
        every { adapter.id } returns id
        return adapter
    }
}
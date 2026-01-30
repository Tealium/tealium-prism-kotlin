package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.settings.modules.DispatcherSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.tests.common.trimJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DispatcherSettingsBuilderTests {

    private lateinit var builder: DefaultDispatcherSettingsBuilder

    @Before
    fun setup() {
        builder = DefaultDispatcherSettingsBuilder("dispatcher")
    }

    @Test
    fun setRules_Sets_String_Rules_In_DataObject() {
        val settings = builder
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
            """.trimJson()
            ), rules
        )
    }

    @Test
    fun setMappings_Sets_Mappings_When_Given_Valid_Mappings_Instance() {
        builder.setMappings {
            mapFrom("source", "destination")
        }

        val settings = builder.build()

        val transformations = settings.getMappings()
        assertNotNull(transformations)
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals(JsonPath["destination"], transformation.destination.path)
        assertEquals(JsonPath["source"], transformation.parameters.reference?.path)
    }

    @Test
    fun setMappings_With_Callback_Sets_Mappings_When_Given_Valid_Mappings_Instance() {
        @Suppress("RedundantSamConstructor")
        builder.setMappings(Callback { mappings ->
            mappings.mapFrom("source", "destination")
        })

        val settings = builder.build()

        val transformations = settings.getMappings()
        assertNotNull(transformations)
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals(JsonPath["destination"], transformation.destination.path)
        assertEquals(JsonPath["source"], transformation.parameters.reference?.path)
    }

    @Test
    fun setMappings_Sets_Multiple_Mappings_When_Given_Complex_Mappings_Instance() {
        builder.setMappings {
            mapFrom("source1", "destination1")
            mapFrom(path("path")["to"]["source2"], "destination2")
            mapFrom("source3", "destination3").ifValueEquals("expected_value")
        }

        val settings = builder.build()

        val transformations = settings.getMappings()
        assertNotNull(transformations)
        assertEquals(3, transformations.size)

        val transformation1 = transformations[0]
        assertEquals(JsonPath["destination1"], transformation1.destination.path)
        assertEquals(JsonPath["source1"], transformation1.parameters.reference?.path)

        val transformation2 = transformations[1]
        assertEquals(JsonPath["destination2"], transformation2.destination.path)
        assertEquals(JsonPath["path"]["to"]["source2"], transformation2.parameters.reference?.path)

        val transformation3 = transformations[2]
        assertEquals(JsonPath["destination3"], transformation3.destination.path)
        assertEquals(JsonPath["source3"], transformation3.parameters.reference?.path)
        assertEquals("expected_value", transformation3.parameters.filter!!.value)
    }

    @Test
    fun setMappings_Can_Use_Custom_Mappings_Builder_Methods() {
        val settingsBuilder = CustomDispatcherSettingsBuilder("custom", ::CustomMappingsBuilder)

        val settings = settingsBuilder.setMappings {
            setKeepMapping("fixed")
            setCustomCommand("cmd")
        }.build()
        val mappings = settings.getMappings()

        val fixedMapping = mappings[0]
        assertEquals(JsonPath["fixed"], fixedMapping.destination.path)
        assertEquals(JsonPath["fixed"], fixedMapping.parameters.reference?.path)
        assertNull(fixedMapping.parameters.filter)
        assertNull(fixedMapping.parameters.mapTo)

        val customCommandMapping = mappings[1]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], customCommandMapping.destination.path)
        assertNull(customCommandMapping.parameters.reference)
        assertNull(customCommandMapping.parameters.filter)
        assertEquals("cmd", customCommandMapping.parameters.mapTo?.value)
    }

    @Test
    fun setMappings_Can_Use_Custom_Mappings_And_Base_Builder_Methods() {
        val settingsBuilder = CustomDispatcherSettingsBuilder("custom", ::CustomMappingsBuilder)

        val settings = settingsBuilder.setMappings {
            setKeepMapping("fixed")
            mapFrom("key", "destination")
        }.build()
        val mappings = settings.getMappings()

        val fixedMapping = mappings[0]
        assertEquals(JsonPath["fixed"], fixedMapping.destination.path)
        assertEquals(JsonPath["fixed"], fixedMapping.parameters.reference?.path)
        assertNull(fixedMapping.parameters.filter)
        assertNull(fixedMapping.parameters.mapTo)

        val from = mappings[1]
        assertEquals(JsonPath["destination"], from.destination.path)
        assertEquals(JsonPath["key"], from.parameters.reference?.path)
        assertNull(from.parameters.filter)
        assertNull(from.parameters.mapTo)
    }

    private fun DataObject.getMappings(): List<TransformationOperation<MappingParameters>> {
        val converter = TransformationOperation.Converter(MappingParameters.Converter)
        return getDataList(
            ModuleSettings.KEY_MAPPINGS,
        )!!.mapNotNull { converter.convert(it) }
    }
}

class DefaultDispatcherSettingsBuilder(moduleType: String) :
    DispatcherSettingsBuilder<Mappings, DefaultDispatcherSettingsBuilder>(
        moduleType,
        Mappings::default
    )

class CustomDispatcherSettingsBuilder<M : Mappings>(moduleType: String, mappingsSupplier: () -> M) :
    DispatcherSettingsBuilder<M, CustomDispatcherSettingsBuilder<M>>(moduleType, mappingsSupplier)

class CustomMappingsBuilder(
    private val mappings: Mappings = Mappings.default()
) : Mappings by mappings {

    fun setKeepMapping(key: String) {
        mappings.keep(key)
    }

    fun setCustomCommand(name: String) {
        mappings.mapCommand(name)
    }
}
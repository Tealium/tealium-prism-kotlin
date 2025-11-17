package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.settings.modules.DispatcherSettingsBuilder
import com.tealium.prism.core.internal.settings.MappingParameters
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.tests.common.trimJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DispatcherSettingsBuilderTests {

    private lateinit var builder: DispatcherSettingsBuilder<*>

    @Before
    fun setup() {
        builder = DispatcherSettingsBuilder("dispatcher")
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
            from("source", "destination")
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
    fun setMappings_Sets_Multiple_Mappings_When_Given_Complex_Mappings_Instance() {
        builder.setMappings {
            from("source1", "destination1")
            from(path("path")["to"]["source2"], "destination2")
            from("source3", "destination3").ifValueEquals("expected_value")
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

    private fun DataObject.getMappings() : List<TransformationOperation<MappingParameters>> {
        val converter = TransformationOperation.Converter(MappingParameters.Converter)
        return getDataList(
            ModuleSettings.KEY_MAPPINGS,
        )!!.mapNotNull { converter.convert(it) }
    }
}


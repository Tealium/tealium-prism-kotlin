package com.tealium.core.api.settings

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.rules.Rule
import com.tealium.core.api.settings.json.TransformationOperation
import com.tealium.core.api.settings.modules.DispatcherSettingsBuilder
import com.tealium.core.internal.settings.MappingParameters
import com.tealium.core.internal.settings.ModuleSettings
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
        assertEquals("destination", transformation.destination.variable)
        assertEquals("source", transformation.parameters.key?.variable)
    }

    @Test
    fun setMappings_Sets_Multiple_Mappings_When_Given_Complex_Mappings_Instance() {
        builder.setMappings {
            from("source1", "destination1")
            from("source2", listOf("path", "to"), "destination2")
            from("source3", "destination3").ifValueEquals("expected_value")
        }

        val settings = builder.build()

        val transformations = settings.getMappings()
        assertNotNull(transformations)
        assertEquals(3, transformations.size)

        val transformation1 = transformations[0]
        assertEquals("destination1", transformation1.destination.variable)
        assertEquals("source1", transformation1.parameters.key?.variable)

        val transformation2 = transformations[1]
        assertEquals("destination2", transformation2.destination.variable)
        assertEquals("source2", transformation2.parameters.key?.variable)
        assertEquals(listOf("path", "to"), transformation2.parameters.key?.path)

        val transformation3 = transformations[2]
        assertEquals("destination3", transformation3.destination.variable)
        assertEquals("source3", transformation3.parameters.key?.variable)
        assertEquals("expected_value", transformation3.parameters.filter!!.value)
    }

    private fun DataObject.getMappings() : List<TransformationOperation<MappingParameters>> {
        val converter = TransformationOperation.Converter(MappingParameters.Converter)
        return getDataList(
            ModuleSettings.KEY_MAPPINGS,
        )!!.mapNotNull { converter.convert(it) }
    }
}


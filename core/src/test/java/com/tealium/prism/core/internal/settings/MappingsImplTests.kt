package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.settings.VariableAccessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MappingsImplTests {

    private lateinit var mappings: MappingsImpl

    @Before
    fun setup() {
        mappings = MappingsImpl()
    }

    @Test
    fun from_Builds_Transformation_Operation_When_Using_Simple_Key_And_Destination() {
        val builder = mappings.from("source", "destination")
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals("destination", transformation.destination.variable)
        assertNull(transformation.destination.path)

        val params = transformation.parameters
        assertEquals("source", params.key?.variable)
        assertNull(params.key?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun from_Builds_Transformation_Operation_When_Using_Key_Path_And_Destination() {
        val builder = mappings.from("source", listOf("path", "to"), "destination")
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals("destination", transformation.destination.variable)
        assertNull(transformation.destination.path)

        val params = transformation.parameters
        assertEquals("source", params.key?.variable)
        assertEquals(listOf("path", "to"), params.key?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun from_Builds_Transformation_Operation_When_Using_Key_And_Destination_Path() {
        val builder = mappings.from("source", "destination", listOf("path", "to"))
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals("destination", transformation.destination.variable)
        assertEquals(listOf("path", "to"), transformation.destination.path)

        val params = transformation.parameters
        assertEquals("source", params.key?.variable)
        assertNull(params.key?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun from_Builds_Transformation_Operation_When_Using_Key_Path_And_Destination_Path() {
        val builder =
            mappings.from("source", listOf("path", "to"), "destination", listOf("dest", "path"))
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals("destination", transformation.destination.variable)
        assertEquals(listOf("dest", "path"), transformation.destination.path)

        val params = transformation.parameters
        assertEquals("source", params.key?.variable)
        assertEquals(listOf("path", "to"), params.key?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun from_Builds_Transformation_Operation_When_Using_Variable_Accessor_Objects() {
        val source = VariableAccessor("source", listOf("path", "to"))
        val destination = VariableAccessor("destination", listOf("dest", "path"))

        val builder = mappings.from(source, destination)
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals("destination", transformation.destination.variable)
        assertEquals(listOf("dest", "path"), transformation.destination.path)

        val params = transformation.parameters
        assertEquals("source", params.key?.variable)
        assertEquals(listOf("path", "to"), params.key?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun variable_Creates_Accessor_When_Using_Single_Path_Component() {
        val accessor = mappings.variable("key")

        assertEquals("key", accessor.variable)
        assertNull(accessor.path)
    }

    @Test
    fun variable_Creates_Accessor_When_Using_Multiple_Path_Components() {
        val accessor = mappings.variable("obj", "sub", "key")

        assertEquals("key", accessor.variable)
        assertEquals(listOf("obj", "sub"), accessor.path)
    }

    @Test(expected = IllegalArgumentException::class)
    fun variable_Throws_Exception_When_Using_Empty_Path() {
        mappings.variable()
    }

    @Test
    fun ifValueEquals_Sets_Filter_When_Called_On_Builder() {
        val builder = mappings.from("source", "destination")
        builder.ifValueEquals("expected_value")

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        val params = transformation.parameters
        assertEquals("expected_value", params.filter?.value)
        assertNull(params.mapTo)
    }

    @Test
    fun build_Returns_All_MappingParameters_When_Multiple_Mappings_Are_Defined() {
        mappings.from("source1", "destination1")
        mappings.from("source2", "destination2").ifValueEquals("value2")
        mappings.constant("mapped3", "destination3").ifValueEquals("source3", "value3")

        val transformations = mappings.build()
        assertEquals(3, transformations.size)

        val transformation1 = transformations[0]
        assertEquals("destination1", transformation1.destination.variable)
        assertEquals("source1", transformation1.parameters.key?.variable)
        assertNull(transformation1.parameters.filter)
        assertNull(transformation1.parameters.mapTo)

        val transformation2 = transformations[1]
        assertEquals("destination2", transformation2.destination.variable)
        assertEquals("source2", transformation2.parameters.key?.variable)
        assertEquals("value2", transformation2.parameters.filter?.value)
        assertNull(transformation2.parameters.mapTo)

        val transformation3 = transformations[2]
        assertEquals("destination3", transformation3.destination.variable)
        assertEquals("source3", transformation3.parameters.key?.variable)
        assertEquals("value3", transformation3.parameters.filter?.value)
        assertEquals("mapped3", transformation3.parameters.mapTo?.value)
    }

    @Test
    fun constant_Builds_Transformation_Operation_With_Constant_Value() {
        val builder = mappings.constant("constant_value", "destination")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals("destination", mapping.destination.variable)
        assertNull(mapping.destination.path)
        assertNull(mapping.parameters.key)
        assertNull(mapping.parameters.filter)
        assertEquals("constant_value", mapping.parameters.mapTo?.value)
    }

    @Test
    fun constant_Builds_Transformation_Operation_With_Destination_Path() {
        val builder = mappings.constant("constant_value", "destination", listOf("path", "to"))
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals("destination", mapping.destination.variable)
        assertEquals(listOf("path", "to"), mapping.destination.path)
        assertNull(mapping.parameters.key)
        assertNull(mapping.parameters.filter)
        assertEquals("constant_value", mapping.parameters.mapTo?.value)
    }

    @Test
    fun constant_Builds_Transformation_Operation_With_VariableAccessor() {
        val destination = VariableAccessor("destination", listOf("dest", "path"))
        val builder = mappings.constant("constant_value", destination)
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals("destination", mapping.destination.variable)
        assertEquals(listOf("dest", "path"), mapping.destination.path)
        assertNull(mapping.parameters.key)
        assertNull(mapping.parameters.filter)
        assertEquals("constant_value", mapping.parameters.mapTo?.value)
    }

    @Test
    fun constant_Builds_Transformation_Operation_With_Options() {
        val destination = VariableAccessor("destination", listOf("dest", "path"))
        val builder = mappings.constant("constant_value", destination)
            .ifValueEquals("key", "expected")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(destination, mapping.destination)
        assertEquals("key", mapping.parameters.key?.variable)
        assertEquals("expected", mapping.parameters.filter?.value)
        assertEquals("constant_value", mapping.parameters.mapTo?.value)
    }

    @Test
    fun keep_Builds_Transformation_Operation_With_Same_Source_And_Destination() {
        val builder = mappings.keep("key_to_keep")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals("key_to_keep", mapping.destination.variable)
        assertNull(mapping.destination.path)
        assertEquals("key_to_keep", mapping.parameters.key?.variable)
        assertNull(mapping.parameters.key?.path)
        assertNull(mapping.parameters.filter)
        assertNull(mapping.parameters.mapTo)
    }

    @Test
    fun keep_Builds_Transformation_Operation_With_Path() {
        val builder = mappings.keep("key_to_keep", listOf("path", "to"))
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals("key_to_keep", mapping.destination.variable)
        assertEquals(listOf("path", "to"), mapping.destination.path)
        assertEquals("key_to_keep", mapping.parameters.key?.variable)
        assertEquals(listOf("path", "to"), mapping.parameters.key?.path)
        assertNull(mapping.parameters.filter)
        assertNull(mapping.parameters.mapTo)
    }

    @Test
    fun keep_Builds_Transformation_Operation_With_VariableAccessor() {
        val keyToKeep = VariableAccessor("key_to_keep", listOf("path", "to"))
        val builder = mappings.keep(keyToKeep)
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(keyToKeep, mapping.destination)
        assertEquals(keyToKeep, mapping.parameters.key)
        assertNull(mapping.parameters.filter)
        assertNull(mapping.parameters.mapTo)
    }

    @Test
    fun keep_Builds_Transformation_Operation_With_Options() {
        val keyToKeep = VariableAccessor("key_to_keep", listOf("path", "to"))
        val builder = mappings.keep(keyToKeep)
            .ifValueEquals("expected")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(keyToKeep, mapping.destination)
        assertEquals(keyToKeep, mapping.parameters.key)
        assertEquals("expected", mapping.parameters.filter?.value)
        assertNull(mapping.parameters.mapTo?.value)
    }
}

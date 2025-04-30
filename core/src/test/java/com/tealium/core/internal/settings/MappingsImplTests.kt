package com.tealium.core.internal.settings

import com.tealium.core.api.settings.VariableAccessor
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
        assertEquals("source", params.key.variable)
        assertNull(params.key.path)
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
        assertEquals("source", params.key.variable)
        assertEquals(listOf("path", "to"), params.key.path)
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
        assertEquals("source", params.key.variable)
        assertNull(params.key.path)
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
        assertEquals("source", params.key.variable)
        assertEquals(listOf("path", "to"), params.key.path)
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
        assertEquals("source", params.key.variable)
        assertEquals(listOf("path", "to"), params.key.path)
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
    fun mapTo_Sets_Mapped_Value_When_Called_On_Builder() {
        val builder = mappings.from("source", "destination")
        builder.mapTo("mapped_value")

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        val params = transformation.parameters
        assertNull(params.filter)
        assertEquals("mapped_value", params.mapTo?.value)
    }

    @Test
    fun builder_Methods_Can_Be_Chained_When_Setting_Multiple_Properties() {
        val builder = mappings.from("source", "destination")
        builder.ifValueEquals("expected_value").mapTo("mapped_value")

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        val params = transformation.parameters
        assertEquals("expected_value", params.filter?.value)
        assertEquals("mapped_value", params.mapTo?.value)
    }

    @Test
    fun build_Returns_All_Transformations_When_Multiple_Mappings_Are_Defined() {
        mappings.from("source1", "destination1")
        mappings.from("source2", "destination2").ifValueEquals("value2")
        mappings.from("source3", "destination3").mapTo("mapped3")

        val transformations = mappings.build()
        assertEquals(3, transformations.size)

        val transformation1 = transformations[0]
        assertEquals("destination1", transformation1.destination.variable)
        assertEquals("source1", transformation1.parameters.key.variable)
        assertNull(transformation1.parameters.filter)
        assertNull(transformation1.parameters.mapTo)

        val transformation2 = transformations[1]
        assertEquals("destination2", transformation2.destination.variable)
        assertEquals("source2", transformation2.parameters.key.variable)
        assertEquals("value2", transformation2.parameters.filter?.value)
        assertNull(transformation2.parameters.mapTo)

        val transformation3 = transformations[2]
        assertEquals("destination3", transformation3.destination.variable)
        assertEquals("source3", transformation3.parameters.key.variable)
        assertNull(transformation3.parameters.filter)
        assertEquals("mapped3", transformation3.parameters.mapTo?.value)
    }
}

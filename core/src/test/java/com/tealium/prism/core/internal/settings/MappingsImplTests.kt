package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
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
    fun mapFrom_Builds_Transformation_Operation_When_Using_Simple_Key_And_Destination() {
        val builder = mappings.mapFrom("source", "destination")
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals(JsonPath["destination"], transformation.destination.path)

        val params = transformation.parameters
        assertEquals(JsonPath["source"], params.reference?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun mapFrom_Builds_Transformation_Operation_When_Using_Key_Path_And_Destination() {
        val builder = mappings.mapFrom(JsonPath["path"]["to"]["source"], "destination")
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals(JsonPath["destination"], transformation.destination.path)

        val params = transformation.parameters
        assertEquals(JsonPath["path"]["to"]["source"], params.reference?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun mapFrom_Builds_Transformation_Operation_When_Using_Key_And_Destination_Path() {
        val builder = mappings.mapFrom("source", JsonPath["path"]["to"]["destination"])
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals(JsonPath["path"]["to"]["destination"], transformation.destination.path)

        val params = transformation.parameters
        assertEquals(JsonPath["source"], params.reference?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun mapFrom_Builds_Transformation_Operation_When_Using_Key_Path_And_Destination_Path() {
        val builder =
            mappings.mapFrom(JsonPath["path"]["to"]["source"], JsonPath["dest"]["path"]["destination"])
        assertNotNull(builder)

        val transformations = mappings.build()
        assertEquals(1, transformations.size)

        val transformation = transformations[0]
        assertEquals(JsonPath["dest"]["path"]["destination"], transformation.destination.path)

        val params = transformation.parameters
        assertEquals(JsonPath["path"]["to"]["source"], params.reference?.path)
        assertNull(params.filter)
        assertNull(params.mapTo)
    }

    @Test
    fun path_Creates_JsonPath_When_Using_Single_Path_Component() {
        val path = mappings.path("key")

        assertEquals(JsonPath["key"], path)
    }

    @Test
    fun path_Creates_JsonPath_When_Using_Multiple_Path_Components() {
        val path = mappings.path("obj")["sub"]["key"]

        assertEquals(JsonPath["obj"]["sub"]["key"], path)
    }

    @Test
    fun ifValueEquals_Sets_Filter_When_Called_On_Builder() {
        val builder = mappings.mapFrom("source", "destination")
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
        mappings.mapFrom("source1", "destination1")
        mappings.mapFrom("source2", "destination2").ifValueEquals("value2")
        mappings.mapConstant("mapped3".asDataItem(), "destination3").ifValueEquals("source3", "value3")

        val transformations = mappings.build()
        assertEquals(3, transformations.size)

        val transformation1 = transformations[0]
        assertEquals(JsonPath["destination1"], transformation1.destination.path)
        assertEquals(JsonPath["source1"], transformation1.parameters.reference?.path)
        assertNull(transformation1.parameters.filter)
        assertNull(transformation1.parameters.mapTo)

        val transformation2 = transformations[1]
        assertEquals(JsonPath["destination2"], transformation2.destination.path)
        assertEquals(JsonPath["source2"], transformation2.parameters.reference?.path)
        assertEquals("value2", transformation2.parameters.filter?.value)
        assertNull(transformation2.parameters.mapTo)

        val transformation3 = transformations[2]
        assertEquals(JsonPath["destination3"], transformation3.destination.path)
        assertEquals(JsonPath["source3"], transformation3.parameters.reference?.path)
        assertEquals("value3", transformation3.parameters.filter?.value)
        assertEquals("mapped3", transformation3.parameters.mapTo?.value?.value)
    }

    @Test
    fun mapConstant_Builds_Transformation_Operation_With_Constant_Value() {
        val builder = mappings.mapConstant("constant_value".asDataItem(), "destination")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(JsonPath["destination"], mapping.destination.path)
        assertNull(mapping.parameters.reference)
        assertNull(mapping.parameters.filter)
        assertEquals("constant_value", mapping.parameters.mapTo?.value?.value)
    }

    @Test
    fun mapConstant_Builds_Transformation_Operation_With_Destination_Path() {
        val destination = JsonPath["path"]["to"]["destination"]
        val builder = mappings.mapConstant("constant_value".asDataItem(), destination)
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(destination, mapping.destination.path)
        assertNull(mapping.parameters.reference)
        assertNull(mapping.parameters.filter)
        assertEquals("constant_value", mapping.parameters.mapTo?.value?.value)
    }

    @Test
    fun mapConstant_Builds_Transformation_Operation_With_Options() {
        val destination = JsonPath["dest"]["path"]["destination"]
        val builder = mappings.mapConstant("constant_value".asDataItem(), destination)
            .ifValueEquals("key", "expected")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(destination, mapping.destination.path)
        assertEquals(JsonPath["key"], mapping.parameters.reference?.path)
        assertEquals("expected", mapping.parameters.filter?.value)
        assertEquals("constant_value", mapping.parameters.mapTo?.value?.value)
    }

    @Test
    fun keep_Builds_Transformation_Operation_With_Same_Source_And_Destination() {
        val builder = mappings.keep("key_to_keep")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(JsonPath["key_to_keep"], mapping.destination.path)
        assertEquals(JsonPath["key_to_keep"], mapping.parameters.reference?.path)
        assertNull(mapping.parameters.filter)
        assertNull(mapping.parameters.mapTo)
    }

    @Test
    fun keep_Builds_Transformation_Operation_With_Path() {
        val path = JsonPath["path"]["to"]["key_to_keep"]
        val builder = mappings.keep(path)
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(path, mapping.destination.path)
        assertEquals(path, mapping.parameters.reference?.path)
        assertNull(mapping.parameters.filter)
        assertNull(mapping.parameters.mapTo)
    }

    @Test
    fun keep_Builds_Transformation_Operation_With_Options() {
        val path = JsonPath["path"]["to"]["key_to_keep"]
        val builder = mappings.keep(path)
            .ifValueEquals("expected")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(path, mapping.destination.path)
        assertEquals(path, mapping.parameters.reference?.path)
        assertEquals("expected", mapping.parameters.filter?.value)
        assertNull(mapping.parameters.mapTo?.value)
    }

    @Test
    fun mapCommand_Builds_Transformation_Operation_With_Command_Name() {
        val builder = mappings.mapCommand("test_command")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], mapping.destination.path)
        assertNull(mapping.parameters.reference)
        assertNull(mapping.parameters.filter)
        assertEquals("test_command", mapping.parameters.mapTo?.value?.value)
    }

    @Test
    fun mapCommand_Builds_Transformation_Operation_With_ForAllEvents_Option() {
        val builder = mappings.mapCommand("event_command")
            .forAllEvents()
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], mapping.destination.path)
        assertEquals(JsonPath[Dispatch.Keys.TEALIUM_EVENT_TYPE], mapping.parameters.reference?.path)
        assertEquals(DispatchType.Event.name, mapping.parameters.filter?.value)
        assertEquals("event_command", mapping.parameters.mapTo?.value?.value)
    }

    @Test
    fun mapCommand_Builds_Transformation_Operation_With_ForAllViews_Option() {
        val builder = mappings.mapCommand("view_command")
            .forAllViews()
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], mapping.destination.path)
        assertEquals(JsonPath[Dispatch.Keys.TEALIUM_EVENT_TYPE], mapping.parameters.reference?.path)
        assertEquals(DispatchType.View.name, mapping.parameters.filter?.value)
        assertEquals("view_command", mapping.parameters.mapTo?.value?.value)
    }

    @Test
    fun mapCommand_Builds_Transformation_Operation_With_IfValueEquals_Key_Option() {
        val builder = mappings.mapCommand("conditional_command")
            .ifValueEquals("custom_key", "expected_value")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], mapping.destination.path)
        assertEquals(JsonPath["custom_key"], mapping.parameters.reference?.path)
        assertEquals("expected_value", mapping.parameters.filter?.value)
        assertEquals("conditional_command", mapping.parameters.mapTo?.value?.value)
    }

    @Test
    fun mapCommand_Builds_Transformation_Operation_With_IfValueEquals_Path_Option() {
        val path = JsonPath["nested"]["path"]["key"]
        val builder = mappings.mapCommand("path_conditional_command")
            .ifValueEquals(path, "expected_value")
        assertNotNull(builder)

        val mappingsList = mappings.build()
        assertEquals(1, mappingsList.size)

        val mapping = mappingsList[0]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], mapping.destination.path)
        assertEquals(path, mapping.parameters.reference?.path)
        assertEquals("expected_value", mapping.parameters.filter?.value)
        assertEquals("path_conditional_command", mapping.parameters.mapTo?.value?.value)
    }

    @Test
    fun build_Returns_Complex_Mixed_Mappings_With_Various_Options() {
        // Complex scenario: mix of all mapping types with various options
        mappings.mapFrom(JsonPath["user"]["profile"]["name"], "customer_name")
        mappings.keep("event_name").ifValueEquals("tracked")
        mappings.mapConstant("analytics_v2".asDataItem(), JsonPath["config"]["version"])
            .ifValueEquals(JsonPath["settings"]["enabled"], "true")
        mappings.mapCommand("purchase").forAllEvents()
        mappings.mapFrom("product_id", JsonPath["ecommerce"]["item"]["id"])
            .ifValueEquals("premium")

        val transformations = mappings.build()
        assertEquals(5, transformations.size)

        // Verify first mapping: nested path to simple key
        val mapping1 = transformations[0]
        assertEquals(JsonPath["customer_name"], mapping1.destination.path)
        assertEquals(JsonPath["user"]["profile"]["name"], mapping1.parameters.reference?.path)
        assertNull(mapping1.parameters.filter)
        assertNull(mapping1.parameters.mapTo)

        // Verify second mapping: keep with condition
        val mapping2 = transformations[1]
        assertEquals(JsonPath["event_name"], mapping2.destination.path)
        assertEquals(JsonPath["event_name"], mapping2.parameters.reference?.path)
        assertEquals("tracked", mapping2.parameters.filter?.value)
        assertNull(mapping2.parameters.mapTo)

        // Verify third mapping: constant with nested destination and path condition
        val mapping3 = transformations[2]
        assertEquals(JsonPath["config"]["version"], mapping3.destination.path)
        assertEquals(JsonPath["settings"]["enabled"], mapping3.parameters.reference?.path)
        assertEquals("true", mapping3.parameters.filter?.value)
        assertEquals("analytics_v2", mapping3.parameters.mapTo?.value?.value)

        // Verify fourth mapping: command for all events
        val mapping4 = transformations[3]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], mapping4.destination.path)
        assertEquals(JsonPath[Dispatch.Keys.TEALIUM_EVENT_TYPE], mapping4.parameters.reference?.path)
        assertEquals(DispatchType.Event.name, mapping4.parameters.filter?.value)
        assertEquals("purchase", mapping4.parameters.mapTo?.value?.value)

        // Verify fifth mapping: simple key to nested path with condition
        val mapping5 = transformations[4]
        assertEquals(JsonPath["ecommerce"]["item"]["id"], mapping5.destination.path)
        assertEquals(JsonPath["product_id"], mapping5.parameters.reference?.path)
        assertEquals("premium", mapping5.parameters.filter?.value)
        assertNull(mapping5.parameters.mapTo)
    }

    @Test
    fun build_Returns_Advanced_Command_And_Conditional_Mappings() {
        // Advanced scenario: multiple commands with different conditions and complex data mappings
        mappings.mapCommand("view_product").forAllViews()
        mappings.mapCommand("add_to_cart")
            .ifValueEquals(JsonPath["event"]["action"], "cart_add")
        mappings.mapFrom(JsonPath["cart"]["items"][0]["price"], JsonPath["analytics"]["item_price"])
            .ifValueEquals("available")
        mappings.mapConstant("mobile_app".asDataItem(), "platform")
            .ifValueEquals("app_version", "2.1.0")
        mappings.keep(JsonPath["user"]["preferences"]["notifications"])

        val transformations = mappings.build()
        assertEquals(5, transformations.size)

        // Verify command for all views
        val mapping1 = transformations[0]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], mapping1.destination.path)
        assertEquals(JsonPath[Dispatch.Keys.TEALIUM_EVENT_TYPE], mapping1.parameters.reference?.path)
        assertEquals(DispatchType.View.name, mapping1.parameters.filter?.value)
        assertEquals("view_product", mapping1.parameters.mapTo?.value?.value)

        // Verify conditional command with nested path condition
        val mapping2 = transformations[1]
        assertEquals(JsonPath[Dispatch.Keys.COMMAND_NAME], mapping2.destination.path)
        assertEquals(JsonPath["event"]["action"], mapping2.parameters.reference?.path)
        assertEquals("cart_add", mapping2.parameters.filter?.value)
        assertEquals("add_to_cart", mapping2.parameters.mapTo?.value?.value)

        // Verify complex array access mapping with condition
        val mapping3 = transformations[2]
        assertEquals(JsonPath["analytics"]["item_price"], mapping3.destination.path)
        assertEquals(JsonPath["cart"]["items"][0]["price"], mapping3.parameters.reference?.path)
        assertEquals("available", mapping3.parameters.filter?.value)
        assertNull(mapping3.parameters.mapTo)

        // Verify constant with simple key condition
        val mapping4 = transformations[3]
        assertEquals(JsonPath["platform"], mapping4.destination.path)
        assertEquals(JsonPath["app_version"], mapping4.parameters.reference?.path)
        assertEquals("2.1.0", mapping4.parameters.filter?.value)
        assertEquals("mobile_app", mapping4.parameters.mapTo?.value?.value)

        // Verify keep with nested path
        val mapping5 = transformations[4]
        assertEquals(JsonPath["user"]["preferences"]["notifications"], mapping5.destination.path)
        assertEquals(JsonPath["user"]["preferences"]["notifications"], mapping5.parameters.reference?.path)
        assertNull(mapping5.parameters.filter)
        assertNull(mapping5.parameters.mapTo)
    }
}

package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.data.get
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.settings.MappingParameters
import com.tealium.prism.core.api.settings.Mappings
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.settings.MappingsImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MappingsEngineTests {

    private lateinit var dispatch: Dispatch
    private lateinit var mappings: StateSubject<Map<String, List<MappingOperation>>>
    private lateinit var mappingsEngine: MappingsEngine

    val payload = DataObject.create {
        put("string", "value")
        put("int", 10)
        put("bool", true)
        put("list", DataList.create {
            add(1)
            add(2)
            add(3)
        })
        put("obj-1", DataObject.create {
            put("key-1", 1)
            put("key-2", 2)
            put("key-3", 3)
        })
    }

    @Before
    fun setUp() {
        dispatch = Dispatch.create("event", dataObject = payload)

        val initialMappings = mapOf(
            "dispatcher1" to buildMappings {
                mapFrom("string", "output_string")
            },
            "empty_mappings" to listOf()
        )

        mappings = Observables.stateSubject(initialMappings)
        mappingsEngine = MappingsEngine(mappings)
    }

    // region Dispatch level

    @Test
    fun map_Returns_Dispatch_With_Mapped_Values_On_Top_Of_Full_Payload() {
        val initialPayload = dispatch.payload()

        val mappedDispatch = mappingsEngine.map("dispatcher1", dispatch)

        val expected = initialPayload.copy {
            put("output_string", payload.getString("string")!!)
        }
        assertEquals(expected, mappedDispatch.payload())
        assertEquals("event", mappedDispatch.payload().getString(Dispatch.Keys.TEALIUM_EVENT))
    }

    @Test
    fun map_Returns_Dispatch_With_Unchanged_Payload_If_Empty_Mappings_Found() {
        val initialPayload = dispatch.payload()

        val mappedDispatch = mappingsEngine.map("empty_mappings", dispatch)

        assertEquals(initialPayload, mappedDispatch.payload())
    }

    @Test
    fun map_Returns_Dispatch_Unaltered_If_No_Mappings_Found() {
        val initialPayload = dispatch.payload()

        val mappedDispatch = mappingsEngine.map("dispatcher_without_settings", dispatch)

        assertEquals(initialPayload, mappedDispatch.payload())
    }

    @Test
    fun map_Uses_Latest_Emitted_Mappings() {
        mappings.onNext(mapOf("empty_mappings" to buildMappings {
            mapFrom("string", "output_string")
        }))

        val mappedDispatch = mappingsEngine.map("empty_mappings", dispatch)

        assertEquals("value", mappedDispatch.payload().getString("output_string"))
    }

    // endregion

    // region Full payload

    @Test
    fun map_DataObject_Returns_Payload_Unchanged_When_Mappings_Empty() {
        val remapped = MappingsEngine.map(payload, listOf())

        assertEquals(payload, remapped)
    }

    @Test
    fun map_DataObject_Keeps_Unmapped_Keys() {
        val mappings = buildMappings {
            mapFrom("string", "new_string")
            mapFrom("int", "new_int")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        val expected = payload.copy {
            put("new_string", "value")
            put("new_int", 10)
        }
        assertEquals(expected, remapped)
    }

    @Test
    fun map_DataObject_Keeps_Source_Key_After_Mapping() {
        val mappings = buildMappings {
            mapFrom("string", "new_string")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("value", remapped.getString("string"))
    }

    @Test
    fun map_DataObject_Maps_Keys_From_Nested_Objects() {
        val mappings = buildMappings {
            mapFrom(path("obj-1")["key-1"], "1")
            mapFrom(path("obj-1")["key-2"], "2")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(payload.copy {
            put("1", 1)
            put("2", 2)
        }, remapped)
    }

    @Test
    fun map_DataObject_Creates_Missing_Objects_On_Path() {
        val mappings = buildMappings {
            mapFrom("string", path("obj-lvl-1")["obj-lvl-2"]["new_string"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        val expected = payload.copy {
            put("obj-lvl-1", DataObject.create {
                put("obj-lvl-2", DataObject.create {
                    put("new_string", "value")
                })
            })
        }
        assertEquals(expected, remapped)
    }

    // endregion

    // region Replacing

    @Test
    fun map_DataObject_Replaces_Existing_Payload_Value_At_Destination() {
        val mappings = buildMappings {
            mapFrom("int", "string")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(10, remapped.getInt("string"))
    }

    @Test
    fun map_DataObject_Replaces_Existing_Payload_Value_With_Constant() {
        val mappings = buildMappings {
            mapConstant("constant".asDataItem(), "string")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("constant", remapped.getString("string"))
    }

    @Test
    fun map_DataObject_Replaces_Duplicate_Destinations() {
        val mappings = buildMappings {
            mapFrom("string", "result")
            mapFrom("int", "result")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(10, remapped.getInt("result"))
    }

    @Test
    fun map_DataObject_Replaces_Nested_Duplicate_Destinations() {
        val mappings = buildMappings {
            mapFrom("string", path("obj")["result"])
            mapFrom("int", path("obj")["result"])
            mapFrom("bool", path("obj")["result"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(true, remapped.getDataObject("obj")!!.getBoolean("result"))
    }

    @Test
    fun map_DataObject_Replaces_Duplicate_Destinations_When_Value_Is_Constant() {
        val mappings = buildMappings {
            mapConstant("value1".asDataItem(), "result")
            mapConstant("value2".asDataItem(), "result")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("value2", remapped.getString("result"))
    }

    @Test
    fun map_DataObject_Replaces_Nested_Duplicate_Destinations_When_Value_Is_Constant() {
        val mappings = buildMappings {
            mapConstant("value1".asDataItem(), path("obj")["result"])
            mapConstant("value2".asDataItem(), path("obj")["result"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("value2", remapped.getDataObject("obj")!!.getString("result"))
    }

    @Test
    fun map_DataObject_Replaces_Existing_Nested_Value_And_Keeps_Siblings() {
        val mappings = buildMappings {
            mapFrom("string", path("obj-1")["key-1"])
            mapFrom("int", path("obj-1")["obj2"]["int"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(DataObject.create {
            put("key-1", "value")
            put("key-2", 2)
            put("key-3", 3)
            put("obj2", DataObject.create {
                put("int", 10)
            })
        }, remapped.getDataObject("obj-1"))
    }

    @Test
    fun map_DataObject_Does_Not_Combine_Constant_With_Value_Inside_Mapped_Parent() {
        val mappings = buildMappings {
            mapFrom("obj-1", "obj")
            mapConstant("constant".asDataItem(), path("obj")["key-1"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("constant", remapped.getDataObject("obj")!!.getString("key-1"))
    }

    @Test
    fun map_DataObject_Replaces_Mismatched_Scalar_Parent_With_Object() {
        val mappings = buildMappings {
            mapFrom("int", path("string")["id"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(DataObject.create { put("id", 10) }, remapped.getDataObject("string"))
    }

    @Test
    fun map_DataObject_Replaces_Mismatched_Object_Parent_With_List() {
        val mappings = buildMappings {
            mapFrom("string", path("obj-1")[0])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(listOf("value").asDataList(), remapped.getDataList("obj-1"))
    }

    // endregion

    // region List indexes

    @Test
    fun map_DataObject_Replaces_Existing_List_Element_At_Index() {
        val mappings = buildMappings {
            mapFrom("string", path("list")[1])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(listOf(1, "value", 3).asDataList(), remapped.getDataList("list"))
    }

    @Test
    fun map_DataObject_Appends_To_Existing_List_When_Index_Is_Size() {
        val mappings = buildMappings {
            mapFrom("int", path("list")[3])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(listOf(1, 2, 3, 10).asDataList(), remapped.getDataList("list"))
    }

    @Test
    fun map_DataObject_Pads_Existing_List_When_Index_Is_Beyond_Size() {
        val mappings = buildMappings {
            mapFrom("int", path("list")[5])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(listOf(1, 2, 3, null, null, 10).asDataList(), remapped.getDataList("list"))
    }

    @Test
    fun map_DataObject_Replaces_Same_List_Index_When_Mapped_Twice() {
        val mappings = buildMappings {
            mapFrom("string", path("list")[0])
            mapFrom("int", path("list")[0])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(listOf(10, 2, 3).asDataList(), remapped.getDataList("list"))
    }

    // endregion

    // region Reading from the original payload

    @Test
    fun map_DataObject_Does_Not_Chain_Mappings() {
        val mappings = buildMappings {
            mapFrom("string", "first")
            mapFrom("first", "second")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("value", remapped.getString("first"))
        assertNull(remapped.get("second"))
    }

    @Test
    fun map_DataObject_Swaps_Values_Between_Keys() {
        val mappings = buildMappings {
            mapFrom("string", "int")
            mapFrom("int", "string")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("value", remapped.getString("int"))
        assertEquals(10, remapped.getInt("string"))
    }

    @Test
    fun map_DataObject_Reads_Source_From_Original_Payload_Regardless_Of_Order() {
        val mappings = buildMappings {
            mapConstant("overwritten".asDataItem(), "string")
            mapFrom("string", "copy")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("overwritten", remapped.getString("string"))
        assertEquals("value", remapped.getString("copy"))
    }

    @Test
    fun map_DataObject_Reads_Filter_From_Original_Payload() {
        val mappings = buildMappings {
            mapConstant("overwritten".asDataItem(), "string")
            mapConstant("applied".asDataItem(), "flag")
                .ifValueEquals("string", "value")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("applied", remapped.getString("flag"))
    }

    // endregion

    // region Filters

    @Test
    fun map_DataObject_Does_Run_Mapping_If_Extracted_Value_Matches_Filter() {
        val mappings = buildMappings {
            mapFrom("string", "output")
                .ifValueEquals("value")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("value", remapped.getString("output"))
    }

    @Test
    fun map_DataObject_Does_Not_Run_Mapping_If_Extracted_Value_Not_Matches_Filter() {
        val mappings = buildMappings {
            mapFrom("string", "output")
                .ifValueEquals("something else")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(payload, remapped)
    }

    @Test
    fun map_DataObject_Keeps_Existing_Value_If_Mapping_Is_Skipped_By_Filter() {
        val mappings = buildMappings {
            mapFrom("int", "string")
                .ifValueEquals("something else")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("value", remapped.getString("string"))
    }

    @Test
    fun map_DataObject_Keeps_Existing_Value_If_Source_Is_Missing() {
        val mappings = buildMappings {
            mapFrom("missing", "string")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(payload, remapped)
    }

    @Test
    fun map_DataObject_Does_Not_Run_Mapping_If_MapTo_Value_Matches_Filter() {
        val mappings = buildMappings {
            mapConstant("non_matching_value".asDataItem(), "output")
                .ifValueEquals("string", "non_matching_value")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(payload, remapped)
    }

    @Test
    fun map_DataObject_Does_Not_Run_Mapping_If_Extracted_Value_Not_Matches_Filter_As_String() {
        val mappings = buildMappings {
            mapFrom("int", "output")
                .ifValueEquals("100")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(payload, remapped)
    }

    @Test
    fun map_DataObject_Does_Run_Mapping_If_Extracted_Value_Matches_Filter_As_String() {
        val mappings = buildMappings {
            mapFrom("int", "output")
                .ifValueEquals("10")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(payload.copy { put("output", 10) }, remapped)
    }

    // endregion

    // region MapTo

    @Test
    fun map_DataObject_Prefers_MapTo_If_Provided() {
        val mappings = listOf(
            TransformationOperation(
                ReferenceContainer.key("string"),
                MappingParameters(
                    reference = ReferenceContainer.key("string"),
                    mapTo = ValueContainer("something else".asDataItem()),
                    filter = null
                )
            )
        )

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("something else", remapped.getString("string"))
    }

    @Test
    fun map_DataObject_Uses_MapTo_Even_If_Extracted_Is_Null() {
        val mappings = listOf(
            TransformationOperation(
                ReferenceContainer.key("string"),
                MappingParameters(
                    reference = ReferenceContainer.key("missing_input"),
                    mapTo = ValueContainer("something else".asDataItem()),
                    filter = null
                )
            )
        )

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("something else", remapped.getString("string"))
    }

    // endregion

    // region command_name

    private val commandName = Dispatch.Keys.COMMAND_NAME

    private fun payloadWithCommands(commands: Any): DataObject = payload.copy {
        putAny(commandName, commands)
    }

    @Test
    fun map_DataObject_Maps_Single_Command_As_Value_When_None_Exists() {
        val mappings = buildMappings {
            mapCommand("a")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("a", remapped.getString(commandName))
    }

    @Test
    fun map_DataObject_Appends_Multiple_Mapped_Commands_In_Order() {
        val mappings = buildMappings {
            mapCommand("a")
            mapCommand("b")
            mapCommand("c")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(listOf("a", "b", "c").asDataList(), remapped.getDataList(commandName))
    }

    @Test
    fun map_DataObject_Appends_Command_To_Payload_String() {
        val mappings = buildMappings {
            mapCommand("b")
        }

        val remapped = MappingsEngine.map(payloadWithCommands("a"), mappings)

        assertEquals(listOf("a", "b").asDataList(), remapped.getDataList(commandName))
    }

    @Test
    fun map_DataObject_Appends_Command_To_Payload_List() {
        val mappings = buildMappings {
            mapCommand("c")
        }

        val remapped = MappingsEngine.map(payloadWithCommands(listOf("a", "b")), mappings)

        assertEquals(listOf("a", "b", "c").asDataList(), remapped.getDataList(commandName))
    }

    @Test
    fun map_DataObject_Appends_Variable_To_Payload_Command() {
        val mappings = buildMappings {
            mapFrom("extra_command", commandName)
        }
        val input = payloadWithCommands("a").copy { put("extra_command", "b") }

        val remapped = MappingsEngine.map(input, mappings)

        assertEquals(listOf("a", "b").asDataList(), remapped.getDataList(commandName))
    }

    @Test
    fun map_DataObject_Appends_Variable_And_Constant_In_Any_Order() {
        val input = payload.copy { put("extra_command", "v") }

        val variableFirst = MappingsEngine.map(input, buildMappings {
            mapFrom("extra_command", commandName)
            mapCommand("c")
        })
        val constantFirst = MappingsEngine.map(input, buildMappings {
            mapCommand("c")
            mapFrom("extra_command", commandName)
        })

        assertEquals(listOf("v", "c").asDataList(), variableFirst.getDataList(commandName))
        assertEquals(listOf("c", "v").asDataList(), constantFirst.getDataList(commandName))
    }

    @Test
    fun map_DataObject_Flattens_List_Variable_One_Level_When_Appending() {
        val mappings = buildMappings {
            mapFrom("extra_commands", commandName)
        }
        val input = payloadWithCommands("a").copy {
            put("extra_commands", DataList.create {
                add("b")
                add(listOf("c").asDataList())
            })
        }

        val remapped = MappingsEngine.map(input, mappings)

        val expected = DataList.create {
            add("a")
            add("b")
            add(listOf("c").asDataList())
        }
        assertEquals(expected, remapped.getDataList(commandName))
    }

    @Test
    fun map_DataObject_Flattens_List_Constant_In_Any_Order() {
        val list = listOf("x", "y").asDataList().asDataItem()

        val listFirst = MappingsEngine.map(payload, buildMappings {
            mapConstant(list, commandName)
            mapCommand("z")
        })
        val listLast = MappingsEngine.map(payload, buildMappings {
            mapCommand("z")
            mapConstant(list, commandName)
        })

        assertEquals(listOf("x", "y", "z").asDataList(), listFirst.getDataList(commandName))
        assertEquals(listOf("z", "x", "y").asDataList(), listLast.getDataList(commandName))
    }

    @Test
    fun map_DataObject_Reads_Original_Command_Name_While_Appending() {
        val mappings = buildMappings {
            mapCommand("b")
            mapFrom(commandName, "copy")
        }

        val remapped = MappingsEngine.map(payloadWithCommands("a"), mappings)

        assertEquals("a", remapped.getString("copy"))
    }

    @Test
    fun map_DataObject_Replaces_Nested_Command_Name() {
        val mappings = buildMappings {
            mapConstant("a".asDataItem(), path("obj")[commandName])
            mapConstant("b".asDataItem(), path("obj")[commandName])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("b", remapped.getDataObject("obj")!!.getString(commandName))
    }

    @Test
    fun map_DataObject_Replaces_Command_Name_List_Element_At_Index() {
        val mappings = buildMappings {
            mapConstant("z".asDataItem(), path(commandName)[0])
        }

        val remapped = MappingsEngine.map(payloadWithCommands(listOf("a", "b")), mappings)

        assertEquals(listOf("z", "b").asDataList(), remapped.getDataList(commandName))
    }

    @Test
    fun map_DataObject_Does_Not_Append_Command_If_Mapping_Is_Skipped() {
        val mappings = buildMappings {
            mapCommand("b").ifValueEquals("string", "something else")
        }

        val remapped = MappingsEngine.map(payloadWithCommands("a"), mappings)

        assertEquals("a", remapped.getString(commandName))
    }

    // endregion

    private fun buildMappings(block: Mappings.() -> Unit): List<MappingOperation> =
        MappingsImpl().apply(block).build()
}
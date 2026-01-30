package com.tealium.prism.core.internal.dispatch

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
import org.junit.Assert.assertTrue
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

    @Test
    fun map_Returns_Dispatch_With_Payload_After_Applying_Mappings() {
        val mappedDispatch = mappingsEngine.map("dispatcher1", dispatch)

        val expected = DataObject.create {
            put("output_string", payload.getString("string")!!)
        }
        assertEquals(expected, mappedDispatch.payload())
    }

    @Test
    fun map_Returns_Dispatch_With_Empty_Payload_If_Empty_Mappings_Found() {
        val mappedDispatch = mappingsEngine.map("empty_mappings", dispatch)

        assertEquals(DataObject.EMPTY_OBJECT, mappedDispatch.payload())
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

    @Test
    fun map_DataObject_Returns_Empty_Object_When_Mappings_Empty() {
        val mappings = listOf<MappingOperation>()

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(DataObject.EMPTY_OBJECT, remapped)
    }

    @Test
    fun map_DataObject_Maps_Only_Listed_Keys() {
        val mappings = buildMappings {
            mapFrom("string", "new_string")
            mapFrom("int", "new_int")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(2, remapped.size)
        assertEquals("value", remapped.getString("new_string"))
        assertEquals(10, remapped.getInt("new_int"))
    }

    @Test
    fun map_DataObject_Maps_Keys_From_Nested_Objects() {
        val mappings = buildMappings {
            mapFrom(path("obj-1")["key-1"], "1")
            mapFrom(path("obj-1")["key-2"], "2")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        val expected = DataObject.create {
            put("1", 1)
            put("2", 2)
        }
        assertEquals(2, remapped.size)
        assertEquals(expected, remapped)
    }

    @Test
    fun map_DataObject_Creates_Missing_Objects_On_Path() {
        val mappings = buildMappings {
            mapFrom("string", path("obj-lvl-1")["obj-lvl-2"]["new_string"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(1, remapped.size)

        val expected = DataObject.create {
            put("obj-lvl-1", DataObject.create {
                put("obj-lvl-2", DataObject.create {
                    put("new_string", "value")
                })
            })
        }
        assertEquals(expected, remapped)
    }

    @Test
    fun map_DataObject_Replaces_Duplicate_Destinations() {
        val mappings = buildMappings {
            mapFrom("string", "result")
            mapFrom("int", "result")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(1, remapped.size)

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

        assertEquals(1, remapped.size)

        assertTrue(remapped.getDataObject("obj")!!.getBoolean("result")!!)
    }

    @Test
    fun map_DataObject_Merges_Duplicate_Destinations_When_Value_Is_Constant() {
        val mappings = buildMappings {
            mapConstant("value1", "result")
            mapConstant("value2", "result")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(1, remapped.size)

        assertEquals(DataList.create {
            add("value1")
            add("value2")
        }, remapped.getDataList("result"))
    }

    @Test
    fun map_DataObject_Merges_Duplicate_Nested_Destinations_When_Value_Is_Constant() {
        val mappings = buildMappings {
            mapConstant("value1", path("obj")["result"])
            mapConstant("value2", path("obj")["result"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(1, remapped.size)

        assertEquals(DataList.create {
            add("value1")
            add("value2")
        }, remapped.getDataObject("obj")!!.getDataList("result")!!)
    }

    @Test
    fun map_DataObject_Merges_Into_Existing_SubObjects_If_Already_Present() {
        val mappings = buildMappings {
            mapFrom("string", path("obj")["obj2"]["string"])
            mapFrom("int", path("obj")["int"])
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(1, remapped.size)

        assertEquals(DataObject.create {
            put("obj2", DataObject.create {
                put("string", "value")
            })
            put("int", 10)
        }, remapped.getDataObject("obj"))
    }

    @Test
    fun map_DataObject_Does_Run_Mapping_If_Extracted_Value_Matches_Filter() {
        val mappings = buildMappings {
            mapFrom("string", "string")
                .ifValueEquals("value")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("value", remapped.getString("string"))
    }

    @Test
    fun map_DataObject_Does_Not_Run_Mapping_If_Extracted_Value_Not_Matches_Filter() {
        val mappings = buildMappings {
            mapFrom("string", "string")
                .ifValueEquals("something else")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(DataObject.EMPTY_OBJECT, remapped)
    }

    @Test
    fun map_DataObject_Does_Not_Run_Mapping_If_MapTo_Value_Matches_Filter() {
        val mappings = buildMappings {
            mapConstant("non_matching_value", "string")
                .ifValueEquals("string", "non_matching_value")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(DataObject.EMPTY_OBJECT, remapped)
    }

    @Test
    fun map_DataObject_Does_Not_Run_Mapping_If_Extracted_Value_Not_Matches_Filter_As_String() {
        val mappings = buildMappings {
            mapFrom("int", "int")
                .ifValueEquals("100")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals(DataObject.EMPTY_OBJECT, remapped)
    }

    @Test
    fun map_DataObject_Does_Run_Mapping_If_Extracted_Value_Matches_Filter_As_String() {
        val mappings = buildMappings {
            mapFrom("int", "int")
                .ifValueEquals("10")
        }

        val remapped = MappingsEngine.map(payload, mappings)

        val expected = DataObject.create {
            put("int", 10)
        }
        assertEquals(expected, remapped)
    }

    @Test
    fun map_DataObject_Prefers_MapTo_If_Provided() {
        val mappings = listOf(
            TransformationOperation(
                ReferenceContainer.key("string"),
                MappingParameters(
                    reference = ReferenceContainer.key("string"),
                    mapTo = ValueContainer("something else"),
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
                    mapTo = ValueContainer("something else"),
                    filter = null
                )
            )
        )

        val remapped = MappingsEngine.map(payload, mappings)

        assertEquals("something else", remapped.getString("string"))
    }

    private fun buildMappings(block: Mappings.() -> Unit): List<MappingOperation> =
        MappingsImpl().apply(block).build()
}
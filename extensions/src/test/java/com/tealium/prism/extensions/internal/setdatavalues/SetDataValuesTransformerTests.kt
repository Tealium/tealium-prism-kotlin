package com.tealium.prism.extensions.internal.setdatavalues

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.extensions.BuildConfig
import com.tealium.prism.extensions.api.setdatavalues.SetDataValuesSettingsBuilder
import com.tealium.prism.extensions.internal.SET_DATA_VALUES
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetDataValuesTransformerTests {

    @MockK
    private lateinit var mockContext: TealiumContext

    private lateinit var transformer: SetDataValuesTransformer


    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        transformer = SetDataValuesTransformer()
    }

    @Test
    fun constructor_WithContext_CreatesInstance() {
        val configuration = DataObject.EMPTY_OBJECT
        val transformerWithContext = SetDataValuesTransformer(mockContext, configuration)

        assertEquals(SET_DATA_VALUES, transformerWithContext.id)
        assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, transformerWithContext.version)
    }

    @Test
    fun id_ReturnsCorrectValue() {
        assertEquals(SET_DATA_VALUES, transformer.id)
    }

    @Test
    fun version_ReturnsCorrectValue() {
        assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, transformer.version)
    }

    @Test
    fun applyTransformation_WithEmptyOperations_CallsCompletionWithOriginalDispatch() {
        val dispatch = Dispatch.create("test", dataObject = DataObject.create {
            put("key", "value")
        })
        val settings = SetDataValuesSettingsBuilder("test")
            .build()

        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify { completion(dispatch) }
        assertEquals("value", dispatch.payload().getString("key"))
    }

    @Test
    fun applyTransformation_WithReferenceOperation_CopiesValueFromSourceToDestination() {
        val dispatch = Dispatch.create("test", dataObject = DataObject.create {
            put("source_key", "source_value")
        })

        val settings = SetDataValuesSettingsBuilder("test")
            .setFrom(ReferenceContainer.key("source_key"), ReferenceContainer.key("destination_key"))
            .build()

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors,
            mockk(relaxed = true)
        )

        val payload = dispatch.payload()
        assertEquals("source_value", payload.getString("destination_key"))
    }

    @Test
    fun applyTransformation_WithConstantOperation_SetsConstantValue() {
        val dispatch = Dispatch.create("test", dataObject = DataObject.create {
            put("existing_key", "existing_value")
        })

        val settings = SetDataValuesSettingsBuilder("test")
            .setConstant("constant_value".asDataItem(), ReferenceContainer.key("destination_key"))
            .build()

        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        val payload = dispatch.payload()
        verify {
            completion(dispatch)
        }

        assertEquals("constant_value", payload.getString("destination_key"))
    }

    @Test
    fun applyTransformation_WithMultipleOperations_AppliesAllOperations() {
        val dispatch = Dispatch.create("test", dataObject = DataObject.create {
            put("source1", "value1")
            put("source2", "value2")
        })

        val settings = SetDataValuesSettingsBuilder("test")
            .setFrom(ReferenceContainer.key("source1"), ReferenceContainer.key("dest1"))
            .setConstant("constant".asDataItem(), ReferenceContainer.key("dest2"))
            .build()

        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        verify {
            completion(dispatch)
        }

        assertEquals("value1", dispatch.payload().getString("dest1"))
        assertEquals("constant", dispatch.payload().getString("dest2"))
    }

    @Test
    fun applyTransformation_WithMissingSourceReference_SkipsOperation() {
        val dispatch = Dispatch.create("test", dataObject = DataObject.create {
            put("existing_key", "existing_value")
        })

        val settings = SetDataValuesSettingsBuilder("test")
            .setFrom(ReferenceContainer.key("missing_key"), ReferenceContainer.key("destination_key"))
            .build()

        val completion = mockk<(Dispatch?) -> Unit>(relaxed = true)

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors,
            completion
        )

        val payload = dispatch.payload()
        verify {
            completion(dispatch)
        }

        assertNull(payload.getString("destination_key"))
    }
}
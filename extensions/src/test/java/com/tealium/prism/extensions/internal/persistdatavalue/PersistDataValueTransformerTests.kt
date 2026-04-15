import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.misc.ExpiryPolicy
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.extensions.api.persistdatavalue.PersistDataValueSettingsBuilder
import com.tealium.prism.extensions.api.persistdatavalue.UpdatePolicy
import com.tealium.prism.extensions.internal.persistdatavalue.PersistDataValueTransformer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.verify
import io.mockk.impl.annotations.RelaxedMockK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistDataValueTransformerTests {

    @RelaxedMockK
    private lateinit var tealiumContext: TealiumContext

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun persistDataValueTransformer_Returns_Correct_IdValue() {
        val transformer = PersistDataValueTransformer(tealiumContext)
        assertEquals("PersistDataValue", transformer.id)
    }

    @Test
    fun applyTransformation_WithInvalidConfiguration_ReturnsOriginalDispatch() {
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create("test_event", dataObject = DataObject.create { put("key", "value") })
        val settings = TransformationSettings(
            "test",
            "1.0",
            setOf(TransformationScope.AfterCollectors),
            DataObject.EMPTY_OBJECT
        )

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors
        ) { result ->
            assertEquals(dispatch, result)
        }
    }

    @Test
    fun applyTransformation_WithReferenceInput_CallsBuildPathOnDataLayer() {
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create(
                "test_event",
                dataObject = DataObject.create { put("source_key", "source_value") })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(
                ReferenceContainer.key("source_key"),
                ReferenceContainer.key("destination_key")
            )
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)
            .build()

        transformer.applyTransformation(settings, dispatch, DispatchScope.AfterCollectors) {}

        verify { tealiumContext.dataLayer.buildPath(JsonPath["destination_key"], "source_value".asDataItem(), any()) }
    }

    @Test
    fun applyTransformation_WithReferenceInput_UpdatesDispatch() {
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create(
                "test_event",
                dataObject = DataObject.create { put("source_key", "source_value") })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(
                ReferenceContainer.key("source_key"),
                ReferenceContainer.key("destination_key")
            )
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)
            .build()

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors
        ) { result ->
            val value = result?.payload()?.extract(JsonPath["destination_key"])
            assertEquals("source_value", value?.value)
        }
    }

    @Test
    fun applyTransformation_WithConstantInput_CallsBuildPathOnDataLayer() {
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create(
                "test_event",
                dataObject = DataObject.create { put("source_key", "source_value") })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistConstant("constant_value".asDataItem(), ReferenceContainer.key("destination_key"))
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)
            .build()

        transformer.applyTransformation(settings, dispatch, DispatchScope.AfterCollectors) {}

        verify { tealiumContext.dataLayer.buildPath(JsonPath["destination_key"], "constant_value".asDataItem(), any()) }
    }

    @Test
    fun applyTransformation_WithConstantInput_UpdatesDispatch() {
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create(
                "test_event",
                dataObject = DataObject.create { put("source_key", "source_value") })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistConstant("constant_value".asDataItem(), ReferenceContainer.key("destination_key"))
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)
            .build()

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors
        ) { result ->
            val value = result?.payload()?.extract(JsonPath["destination_key"])
            assertEquals("constant_value", value?.value)
        }
    }

    @Test
    fun applyTransformation_WithMissingReferenceInput_ReturnsOriginalDispatch() {
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create(
                "test_event",
                dataObject = DataObject.create { put("source_key", "source_value") })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(
                ReferenceContainer.key("test_key"),
                ReferenceContainer.key("destination_key")
            )
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)
            .build()

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors
        ) { result ->
            val payload = result?.payload()
            val value = payload?.extract(JsonPath["destination_key"])
            assertNull(value)
            assertEquals(dispatch.payload(), payload)

        }
    }

    @Test
    fun applyTransformation_WritesValue_WhenKeepFirstValuePolicy_AndNoExistingValue() {
        every { tealiumContext.dataLayer.extract(any()) } returns null
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create("test_event", dataObject = DataObject.create {
                put("source_key", "new_value")
            })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(
                ReferenceContainer.key("source_key"),
                ReferenceContainer.key("destination_key")
            )
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.KEEP_FIRST_VALUE)
            .build()

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors
        ) { result ->
            val payload = result?.payload()
            val value = payload?.extract(JsonPath["destination_key"])
            assertEquals("new_value", value?.value)
        }
    }

    @Test
    fun applyTransformation_KeepsExistingValue_WhenUpdatePolicyIsKeepFirstValue() {
        every { tealiumContext.dataLayer.extract(any()) } returns "existing_value".asDataItem()
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create("test_event", dataObject = DataObject.create {
                put("source_key", "new_value")
                put("destination_key", "existing_value")
            })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(
                ReferenceContainer.key("source_key"),
                ReferenceContainer.key("destination_key")
            )
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.KEEP_FIRST_VALUE)
            .build()

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors
        ) { result ->
            val payload = result?.payload()
            val value = payload?.extract(JsonPath["destination_key"])
            assertEquals("existing_value", value?.value)
        }
    }

    @Test
    fun applyTransformation_UpdatesValue_WhenUpdatePolicyIsAllowUpdate() {
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create("test_event", dataObject = DataObject.create {
                put("source_key", "new_value")
                put("destination_key", "existing_value")
            })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(
                ReferenceContainer.key("source_key"),
                ReferenceContainer.key("destination_key")
            )
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)
            .build()

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors
        ) { result ->
            val payload = result?.payload()
            val value = payload?.extract(JsonPath["destination_key"])
            assertEquals("new_value", value?.value)
        }
    }

    @Test
    fun applyTransformation_KeepsOriginalDispatch_WhenBuildPathFails() {
        every { tealiumContext.dataLayer.buildPath(any(), any(), any()) } throws Exception("build path failed")
        val transformer = PersistDataValueTransformer(tealiumContext)
        val dispatch =
            Dispatch.create(
                "test_event",
                dataObject = DataObject.create { put("source_key", "new_value") })

        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(
                ReferenceContainer.key("source_key"),
                ReferenceContainer.key("destination_key")
            )
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)
            .build()

        transformer.applyTransformation(
            settings,
            dispatch,
            DispatchScope.AfterCollectors
        ) { result ->
            assertEquals(dispatch, result)
            assertEquals(dispatch.payload(), result?.payload())
        }
    }

}
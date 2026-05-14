package com.tealium.prism.extensions.internal.persistdatavalue

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.api.data.ValueSource
import com.tealium.prism.core.api.misc.ExpiryPolicy
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.extensions.api.persistdatavalue.PersistDataValueSettingsBuilder
import com.tealium.prism.extensions.api.persistdatavalue.UpdatePolicy
import com.tealium.prism.extensions.configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PersistDataValueConfigurationTests {

    private val input = ReferenceContainer.key("input_key")
    private val destination = ReferenceContainer.key("destination_key")

    @Test
    fun converter_WithReferenceInput_ReturnsConfiguration() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(ValueSource.Reference(input), config?.input)
        assertEquals(destination, config?.destination)
        assertEquals(ExpiryPolicy.SESSION, config?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, config?.updatePolicy)
    }

    @Test
    fun converter_WithConstantInput_ReturnsConfiguration() {
        val value = ValueContainer("test_value".asDataItem())
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, value)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(ValueSource.Constant(value), config?.input)
        assertEquals(destination, config?.destination)
        assertEquals(ExpiryPolicy.SESSION, config?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, config?.updatePolicy)
    }

    @Test
    fun converter_WithForeverExpiryPolicy_ReturnsForeverPolicy() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_DURATION, ExpiryPolicy.FOREVER)
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(ExpiryPolicy.FOREVER, config?.expiryPolicy)
    }

    @Test
    fun converter_WithKeepFirstValueUpdatePolicy_ReturnsKeepFirstValuePolicy() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_UPDATE_POLICY, UpdatePolicy.KEEP_FIRST_VALUE)
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(UpdatePolicy.KEEP_FIRST_VALUE, config?.updatePolicy)
    }

    @Test
    fun converter_MissingInput_ReturnsNull() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())
        assertNull(config)
    }

    @Test
    fun converter_MissingDestination_ReturnsNull() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())
        assertNull(config)
    }

    @Test
    fun converter_WithUnrecognizedKeys_ReturnsNull() {
        val emptyOperation = DataObject.create {
            put("operation", DataObject.EMPTY_OBJECT)
        }
        val config = PersistDataValueConfiguration.Converter.convert(emptyOperation.asDataItem())
        assertNull(config)
    }

    @Test
    fun converter_WithEmptyData_ReturnsNull() {
        val config =
            PersistDataValueConfiguration.Converter.convert(DataObject.EMPTY_OBJECT.asDataItem())
        assertNull(config)
    }

    @Test
    fun roundTrip_WithReferenceInput_PreservesAllFields() {
        val input = ReferenceContainer.key("source_key")
        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(input, destination)
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)

        val config =
            PersistDataValueConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertEquals(ValueSource.Reference(input), config?.input)
        assertEquals(destination, config?.destination)
        assertEquals(ExpiryPolicy.SESSION, config?.expiryPolicy)
        assertEquals(UpdatePolicy.ALLOW_UPDATE, config?.updatePolicy)
    }

    @Test
    fun roundTrip_WithConstantInput_PreservesAllFields() {
        val settings = PersistDataValueSettingsBuilder("test")
            .persistConstant("constant_value".asDataItem(), destination)
            .setExpiryPolicy(ExpiryPolicy.SESSION)
            .setUpdatePolicy(UpdatePolicy.ALLOW_UPDATE)

        val config =
            PersistDataValueConfiguration.Converter.convert(settings.configuration.asDataItem())

        val expectedValue = ValueContainer("constant_value".asDataItem())
        assertEquals(ValueSource.Constant(expectedValue), config?.input)
        assertEquals(destination, config?.destination)
    }

    @Test
    fun roundTrip_WithForeverExpiry_PreservesExpiryPolicy() {
        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(ReferenceContainer.key("source_key"), destination)
            .setExpiryPolicy(ExpiryPolicy.FOREVER)

        val config =
            PersistDataValueConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertEquals(ExpiryPolicy.FOREVER, config?.expiryPolicy)
    }

    @Test
    fun roundTrip_WithUntilRestartExpiry_PreservesExpiryPolicy() {
        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(ReferenceContainer.key("source_key"), destination)
            .setExpiryPolicy(ExpiryPolicy.UNTIL_RESTART)

        val config =
            PersistDataValueConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertEquals(ExpiryPolicy.UNTIL_RESTART, config?.expiryPolicy)
    }

    @Test
    fun roundTrip_WithDurationExpiry_PreservesExpiryPolicy() {
        val duration = ExpiryPolicy.duration(5.seconds)
        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(ReferenceContainer.key("source_key"), destination)
            .setExpiryPolicy(duration)

        val config =
            PersistDataValueConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertEquals(duration, config?.expiryPolicy)
    }

    @Test
    fun roundTrip_WithKeepFirstValuePolicy_PreservesUpdatePolicy() {
        val settings = PersistDataValueSettingsBuilder("test")
            .persistFrom(ReferenceContainer.key("source_key"), destination)
            .setUpdatePolicy(UpdatePolicy.KEEP_FIRST_VALUE)

        val config =
            PersistDataValueConfiguration.Converter.convert(settings.configuration.asDataItem())

        assertEquals(UpdatePolicy.KEEP_FIRST_VALUE, config?.updatePolicy)
    }

    @Test
    fun converter_WithNonObjectInput_ReturnsNull() {
        val config =
            PersistDataValueConfiguration.Converter.convert(DataItem.string("not_an_object"))
        assertNull(config)
    }

    @Test
    fun converter_WithInvalidDurationValue_FallsBackToSessionDefault() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_DURATION, -99L)
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(ExpiryPolicy.SESSION, config?.expiryPolicy)
    }

    @Test
    fun converter_WithUnknownUpdatePolicyValue_FallsBackToAllowUpdateDefault() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_UPDATE_POLICY, "unknown_policy")
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(UpdatePolicy.ALLOW_UPDATE, config?.updatePolicy)
    }

    @Test
    fun converter_WithPositiveDurationSeconds_ReturnsDurationPolicy() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_DURATION, 5L)
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(ExpiryPolicy.duration(5.seconds), config?.expiryPolicy)
    }

    @Test
    fun converter_WithUpperCaseAllowUpdatePolicy_ParsesCorrectly() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_UPDATE_POLICY, "ALLOWUPDATE")
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(UpdatePolicy.ALLOW_UPDATE, config?.updatePolicy)
    }

    @Test
    fun converter_WithUpperCaseKeepFirstValuePolicy_ParsesCorrectly() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_UPDATE_POLICY, "KEEPFIRSTVALUE")
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(UpdatePolicy.KEEP_FIRST_VALUE, config?.updatePolicy)
    }

    @Test
    fun converter_WithMixedCaseAllowUpdatePolicy_ParsesCorrectly() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_UPDATE_POLICY, "AllowUpdate")
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(UpdatePolicy.ALLOW_UPDATE, config?.updatePolicy)
    }

    @Test
    fun converter_WithMixedCaseKeepFirstValuePolicy_ParsesCorrectly() {
        val dataObject = DataObject.create {
            put(PersistDataValueConfiguration.Converter.KEY_INPUT, input)
            put(PersistDataValueConfiguration.Converter.KEY_DESTINATION, destination)
            put(PersistDataValueConfiguration.Converter.KEY_UPDATE_POLICY, "KeepFirstValue")
        }
        val config = PersistDataValueConfiguration.Converter.convert(dataObject.asDataItem())

        assertEquals(UpdatePolicy.KEEP_FIRST_VALUE, config?.updatePolicy)
    }
}

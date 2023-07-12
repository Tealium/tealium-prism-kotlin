package com.tealium.core.api.data.bundle

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TealiumSerializableTests {

    private val listSerializable = TestListSerializable("value", 10)
    private val bundleSerializable = TestBundleSerializable("value", 10)

    @Test
    fun serialize_ListSerializable_CreatesTealiumList() {
        val serialized = listSerializable.asTealiumValue()

        val deserialized = TestListSerializable
            .Deserializer
            .deserialize(serialized)

        assertEquals("[\"value\",10]", serialized.toString())
        assertEquals(listSerializable, deserialized)
    }

    @Test
    fun serialize_BundleSerializable_CreatesTealiumBundle() {
        val serialized = bundleSerializable.asTealiumValue()

        val deserialized = TestBundleSerializable
            .Deserializer
            .deserialize(serialized)

        assertEquals("{\"string\":\"value\",\"int\":10}", serialized.toString())
        assertEquals(bundleSerializable, deserialized)
    }
}


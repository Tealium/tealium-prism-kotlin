package com.tealium.prism.core.api.data

import org.junit.Assert.assertSame
import org.junit.Test

class JsonObjectPathConvertibleTests {

    @Test
    fun asJsonPath_Returns_Object_Path() {
        val path = JsonPath["key"]
        val objectConvertible = object : JsonObjectPathConvertible {
            override fun asJsonObjectPath(): JsonObjectPath = path
        }
        val convertible: JsonPathConvertible<JsonPath.Component.Key> = objectConvertible

        assertSame(path, convertible.asJsonPath())
    }
}

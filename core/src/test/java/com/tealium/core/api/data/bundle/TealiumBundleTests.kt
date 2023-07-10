package com.tealium.core.api.data.bundle

import org.junit.Assert
import org.junit.Test

class TealiumBundleTests {


    @Test
    fun copy_Should_CreateNewInstance() {
        val bundle = TealiumBundle.Builder()
            .put("string", "string")
            .put("int", 1)
            .getBundle()

        val copy = bundle.copy {
            put("string", "new_value")
        }

        Assert.assertNotSame(bundle, copy)
        Assert.assertNotEquals("new_value", bundle.getString("string"))
        Assert.assertEquals("new_value", copy.getString("string"))
    }

}
package com.tealium.prism.core.internal.modules.collect

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.settings.modules.CollectSettingsBuilder
import com.tealium.prism.core.internal.settings.ModuleSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class CollectModuleConfigurationTests {

    private val localhost = URL("https://localhost/")

    @Test
    fun fromDataObject_Uses_UrlOverrides() {
        val config = CollectModuleConfiguration.fromDataObject(
            createConfigurationObject {
                it.setUrl(localhost.toString())
                    .setBatchUrl(localhost.toString())
            }
        )

        assertEquals(localhost, config?.url)
        assertEquals(localhost, config?.batchUrl)
    }

    @Test
    fun fromDataObject_PrefersUrlOverrides_ToDomainOverride() {
        val config = CollectModuleConfiguration.fromDataObject(
            createConfigurationObject {
                it.setUrl(localhost.toString())
                    .setBatchUrl(localhost.toString())
                    .setDomain("domain")
            }
        )!!

        assertFalse(config.url.toString().contains("domain"))
        assertFalse(config.batchUrl.toString().contains("domain"))
        assertEquals(localhost, config.url)
        assertEquals(localhost, config.batchUrl)
    }

    @Test
    fun fromDataObject_UsesDomainOverride_WhenNoUrlOverride() {
        val config = CollectModuleConfiguration.fromDataObject(
            createConfigurationObject { it.setDomain("domain") }
        )

        assertEquals(URL("https://domain/event"), config?.url)
        assertEquals(URL("https://domain/bulk-event"), config?.batchUrl)
    }

    @Test
    fun fromDataObject_Returns_Null_When_Invalid_Url_Provided() {
        val config = CollectModuleConfiguration.fromDataObject(
            createConfigurationObject {
                it.setUrl("some_invalid_url")
            }
        )

        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Null_When_Invalid_BatchUrl_Provided() {
        val config = CollectModuleConfiguration.fromDataObject(
            createConfigurationObject {
                it.setBatchUrl("some_invalid_url")
            }
        )

        assertNull(config)
    }
}

inline fun createConfigurationObject(
    block: (CollectSettingsBuilder) -> Unit
): DataObject {
    val builder = CollectSettingsBuilder()
    block.invoke(builder)
    return builder.build().getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
}
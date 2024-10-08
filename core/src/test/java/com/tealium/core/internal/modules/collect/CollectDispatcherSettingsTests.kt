package com.tealium.core.internal.modules.collect

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.settings.CollectDispatcherSettingsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class CollectDispatcherSettingsTests {

    private val localhost = URL("https://localhost/")

    @Test
    fun fromDataObject_Uses_UrlOverrides() {
        val settings = CollectDispatcherSettings.fromDataObject(
            createSettings {
                it.setUrl(localhost.toString())
                    .setBatchUrl(localhost.toString())
            }
        )

        assertEquals(localhost, settings?.url)
        assertEquals(localhost, settings?.batchUrl)
    }

    @Test
    fun fromDataObject_PrefersUrlOverrides_ToDomainOverride() {
        val settings = CollectDispatcherSettings.fromDataObject(
            createSettings {
                it.setUrl(localhost.toString())
                    .setBatchUrl(localhost.toString())
                    .setDomain("domain")
            }
        )!!

        assertFalse(settings.url.toString().contains("domain"))
        assertFalse(settings.batchUrl.toString().contains("domain"))
        assertEquals(localhost, settings.url)
        assertEquals(localhost, settings.batchUrl)
    }

    @Test
    fun fromDataObject_UsesDomainOverride_WhenNoUrlOverride() {
        val settings = CollectDispatcherSettings.fromDataObject(
            createSettings { it.setDomain("domain") }
        )

        assertEquals(URL("https://domain/event"), settings?.url)
        assertEquals(URL("https://domain/bulk-event"), settings?.batchUrl)
    }

    @Test
    fun fromDataObject_Returns_Null_When_Invalid_Url_Provided() {
        val collectSettings = CollectDispatcherSettings.fromDataObject(
            createSettings {
                it.setUrl("some_invalid_url")
            }
        )

        assertNull(collectSettings)
    }

    @Test
    fun fromDataObject_Returns_Null_When_Invalid_BatchUrl_Provided() {
        val collectSettings = CollectDispatcherSettings.fromDataObject(
            createSettings {
                it.setBatchUrl("some_invalid_url")
            }
        )

        assertNull(collectSettings)
    }
}

inline fun createSettings(
    block: (CollectDispatcherSettingsBuilder) -> Unit
): DataObject {
    val builder = CollectDispatcherSettingsBuilder()
    block.invoke(builder)
    return builder.build()
}
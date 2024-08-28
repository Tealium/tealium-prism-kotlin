package com.tealium.core.internal.modules.collect

import com.tealium.core.api.data.TealiumBundle
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
    fun fromBundle_Uses_UrlOverrides() {
        val settings = CollectDispatcherSettings.fromBundle(
            createSettings {
                it.setUrl(localhost.toString())
                    .setBatchUrl(localhost.toString())
            }
        )

        assertEquals(localhost, settings?.url)
        assertEquals(localhost, settings?.batchUrl)
    }

    @Test
    fun fromBundle_PrefersUrlOverrides_ToDomainOverride() {
        val settings = CollectDispatcherSettings.fromBundle(
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
    fun fromBundle_UsesDomainOverride_WhenNoUrlOverride() {
        val settings = CollectDispatcherSettings.fromBundle(
            createSettings { it.setDomain("domain") }
        )

        assertEquals(URL("https://domain/event"), settings?.url)
        assertEquals(URL("https://domain/bulk-event"), settings?.batchUrl)
    }

    @Test
    fun fromBundle_Returns_Null_When_Invalid_Url_Provided() {
        val collectSettings = CollectDispatcherSettings.fromBundle(
            createSettings {
                it.setUrl("some_invalid_url")
            }
        )

        assertNull(collectSettings)
    }

    @Test
    fun fromBundle_Returns_Null_When_Invalid_BatchUrl_Provided() {
        val collectSettings = CollectDispatcherSettings.fromBundle(
            createSettings {
                it.setBatchUrl("some_invalid_url")
            }
        )

        assertNull(collectSettings)
    }
}

inline fun createSettings(
    block: (CollectDispatcherSettingsBuilder) -> Unit
): TealiumBundle {
    val builder = CollectDispatcherSettingsBuilder()
    block.invoke(builder)
    return builder.build()
}
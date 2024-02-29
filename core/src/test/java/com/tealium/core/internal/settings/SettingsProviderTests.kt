package com.tealium.core.internal.settings

import com.tealium.core.internal.SdkSettings
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsProviderTests {

    @Test
    fun onSdkSettingsEmitValidValuesForUpdateSdkSettings() {
        val settingsProvider = SettingsProviderImpl()
        val testSettings = mockk<SdkSettings>()
        val onNext: (SdkSettings) -> Unit = mockk(relaxed = true)

        settingsProvider.onSdkSettingsUpdated.take(2)
            .subscribe(onNext)
        settingsProvider.updateSdkSettings(testSettings)

        assertEquals(testSettings, settingsProvider.onSdkSettingsUpdated.value)
        verify {
            onNext(testSettings)
        }
    }

    @Test
    fun getLastRefreshTimeReturnsValidValue() {
        val settingsProvider = SettingsProviderImpl()
        val testTimestamp = System.currentTimeMillis()

        settingsProvider.updateLastRefreshTime(testTimestamp)

        assertEquals(testTimestamp, settingsProvider.getLastRefreshTime())
    }
}
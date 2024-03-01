package com.tealium.core

import android.app.Application
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.tests.common.awaitCreateTealiumImpl
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TealiumTests {

    lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
    }

    @Test
    fun tealium_ReturnsErrorOnReady_WhenDatabaseFailsToInitialize() = runBlocking {
        val exception = Exception()
        val mockDbProvider = mockk<DatabaseProvider>()
        every { mockDbProvider.database } throws exception

        awaitCreateTealiumImpl(getDefaultConfig(app), mockDbProvider) { _, err ->
            assertNotNull(err)
            assertEquals(exception, err)
        }

        Unit
    }
}
package com.tealium.core

import android.app.Application
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.tests.common.awaitCreateTealiumImpl
import com.tealium.tests.common.getDefaultConfig
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TealiumTests {

    @MockK
    lateinit var app: Application

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { app.filesDir } returns File("")
        every { app.applicationContext } returns app
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
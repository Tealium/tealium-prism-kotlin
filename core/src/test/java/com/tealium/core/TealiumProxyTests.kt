package com.tealium.core

import android.app.Application
import com.tealium.core.api.PersistenceException
import com.tealium.core.api.TealiumResult
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.tests.common.createTealiumProxy
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TealiumProxyTests {

    lateinit var app: Application
    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
    }
    @Test
    fun tealium_ReturnsErrorOnReady_WhenDatabaseFailsToInitialize() {
        val exception = Exception()
        val mockDbProvider = mockk<DatabaseProvider>()
        every { mockDbProvider.database } throws exception
        val onComplete = mockk<TealiumCallback<TealiumResult<Tealium>>>(relaxed = true)

        createTealiumProxy(getDefaultConfig(app), mockDbProvider, onReady = onComplete)

        verify(timeout = 1000) {
            onComplete.onComplete(match { result ->
                val thrown = result.exceptionOrNull()!!
                result.isFailure
                        && thrown is PersistenceException
                        && thrown.cause == exception
            })
        }
    }
}
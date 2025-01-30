package com.tealium.lifecycle

import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.lifecycle.internal.LifecycleStorageKey
import com.tealium.lifecycle.internal.LifecycleStorage
import com.tealium.lifecycle.internal.LifecycleStorageImpl
import com.tealium.tests.common.mockkEditor
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LifecycleStorageTests {

    @RelaxedMockK
    private lateinit var mockDataStore: DataStore

    @RelaxedMockK
    private lateinit var mockEditor: DataStore.Editor

    private lateinit var lifecycleStorage: LifecycleStorage

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { mockDataStore.edit() } returns mockEditor
        mockkEditor(mockEditor)

        lifecycleStorage = LifecycleStorageImpl(mockDataStore)
    }

    @Test
    fun registerLaunch_SavesValues() {
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_LAUNCH) } returns 0
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_LAUNCH) } returns 0
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_WAKE) } returns 0
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_WAKE) } returns 0

        lifecycleStorage.registerLaunch(100L)

        verify {
            // set last wake timestamp
            mockEditor.put(LifecycleStorageKey.TIMESTAMP_LAST_WAKE, 100L, Expiry.FOREVER)

            // set last launch event
            mockEditor.put(LifecycleStorageKey.LAST_EVENT, LifecycleEvent.Launch, Expiry.FOREVER)

            // increment launch
            mockEditor.put(LifecycleStorageKey.COUNT_LAUNCH, 1, Expiry.FOREVER)
            mockEditor.put(LifecycleStorageKey.COUNT_TOTAL_LAUNCH, 1, Expiry.FOREVER)

            // increment wake
            mockEditor.put(LifecycleStorageKey.COUNT_WAKE, 1, Expiry.FOREVER)
            mockEditor.put(LifecycleStorageKey.COUNT_TOTAL_WAKE, 1, Expiry.FOREVER)

            // set last launch timestamp
            mockEditor.put(LifecycleStorageKey.TIMESTAMP_LAST_LAUNCH, 100L, Expiry.FOREVER)

            mockEditor.commit()
        }
    }

    @Test
    fun registerWake_SavesValues() {
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_WAKE) } returns 0
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_WAKE) } returns 0

        lifecycleStorage.registerWake(100L)

        verify {
            // set last wake timestamp
            mockEditor.put(LifecycleStorageKey.TIMESTAMP_LAST_WAKE, 100L, Expiry.FOREVER)

            // set last wake event
            mockEditor.put(LifecycleStorageKey.LAST_EVENT, LifecycleEvent.Wake, Expiry.FOREVER)

            // increment wake
            mockEditor.put(LifecycleStorageKey.COUNT_WAKE, 1, Expiry.FOREVER)
            mockEditor.put(LifecycleStorageKey.COUNT_TOTAL_WAKE, 1, Expiry.FOREVER)

            mockEditor.commit()
        }
    }

    @Test
    fun registerSleep_savesValues() {
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_SLEEP) } returns 0
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_SLEEP) } returns 0
        every { mockDataStore.getInt(LifecycleStorageKey.TOTAL_SECONDS_AWAKE) } returns 0
        every { mockDataStore.getInt(LifecycleStorageKey.PRIOR_SECONDS_AWAKE) } returns 0

        lifecycleStorage.registerSleep(100L, 5)

        verify {
            // set last sleep event
            mockEditor.put(LifecycleStorageKey.LAST_EVENT, LifecycleEvent.Sleep, Expiry.FOREVER)

            // increment sleep
            mockEditor.put(LifecycleStorageKey.COUNT_SLEEP, 1, Expiry.FOREVER)
            mockEditor.put(LifecycleStorageKey.COUNT_TOTAL_SLEEP, 1, Expiry.FOREVER)

            // update seconds awake
            mockEditor.put(LifecycleStorageKey.TOTAL_SECONDS_AWAKE, 5L, Expiry.FOREVER)
            mockEditor.put(LifecycleStorageKey.PRIOR_SECONDS_AWAKE, 5L, Expiry.FOREVER)

            // set last sleep timestamp
            mockEditor.put(LifecycleStorageKey.TIMESTAMP_LAST_SLEEP, 100L, Expiry.FOREVER)

            mockEditor.commit()
        }
    }

    @Test
    fun firstLaunchTimestamp_SavesValues() {
        every { mockDataStore.getLong(LifecycleStorageKey.TIMESTAMP_FIRST_LAUNCH) } returns null

        lifecycleStorage.setFirstLaunchTimestamp(100L)

        verify {
            mockEditor.put(LifecycleStorageKey.TIMESTAMP_FIRST_LAUNCH, 100L, Expiry.FOREVER)
            mockEditor.commit()
        }
    }

    @Test
    fun setter_CurrentAppVersion_SavesValue() {
        lifecycleStorage.setCurrentAppVersion("10")

        verify {
            mockEditor.put(LifecycleStorageKey.APP_VERSION, "10", Expiry.FOREVER)
            mockEditor.commit()
        }
    }

    @Test
    fun resets_AfterAppUpdate_SavesValue() {
        lifecycleStorage.resetCountsAfterAppUpdate(100L, "11")

        verify {
            mockEditor.put(LifecycleStorageKey.APP_VERSION, "11", Expiry.FOREVER)
            mockEditor.remove(LifecycleStorageKey.COUNT_LAUNCH)
            mockEditor.remove(LifecycleStorageKey.COUNT_WAKE)
            mockEditor.remove(LifecycleStorageKey.COUNT_SLEEP)
            mockEditor.commit()
        }
    }

    @Test
    fun incrementCrash_SavesValues() {
        every { mockDataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_CRASH) } returns 0

        lifecycleStorage.incrementCrash()

        verify {
            mockEditor.put(LifecycleStorageKey.COUNT_TOTAL_CRASH, 1, Expiry.FOREVER)
            mockEditor.commit()
        }
    }
}
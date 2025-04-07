package com.tealium.lifecycle

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.Logger
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.api.tracking.Tracker
import com.tealium.lifecycle.internal.LifecycleConfiguration
import com.tealium.lifecycle.internal.LifecycleModule
import com.tealium.lifecycle.internal.LifecycleService
import com.tealium.lifecycle.internal.StateKey
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LifecycleModuleTests {

    @RelaxedMockK
    lateinit var mockConfiguration: LifecycleConfiguration

    @RelaxedMockK
    private lateinit var mockLifecycleService: LifecycleService

    @RelaxedMockK
    lateinit var tracker: Tracker

    @RelaxedMockK
    lateinit var logger: Logger

    lateinit var lifecycleModule: LifecycleModule
    lateinit var applicationStatus: Subject<ActivityManager.ApplicationStatus>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        applicationStatus = Observables.replaySubject()

        lifecycleModule = LifecycleModule(
            mockConfiguration,
            mockLifecycleService,
            applicationStatus,
            tracker,
            logger
        )
    }

    @Test
    fun autoTrackingDisabled_RegistersLaunchEvent() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns null
        lifecycleModule.hasLaunched = false

        lifecycleModule.launch()

        verify {
            mockLifecycleService.registerLaunch(any())
        }
    }

    @Test
    fun autoTrackingDisabled_RegistersWakeEvent() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Sleep

        lifecycleModule.wake()

        verify {
            mockLifecycleService.registerWake(any())
        }
    }

    @Test
    fun autoTrackingDisabled_RegistersSleepEvent() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Launch

        lifecycleModule.sleep()

        verify {
            mockLifecycleService.registerSleep(any())
        }
    }

    @Test
    fun autoTrackingDisabled_RegistersLaunchEvent_MergesCustomDataObject() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns null
        every { mockLifecycleService.registerLaunch(any()) } returns DataObject.create { put(StateKey.LIFECYCLE_LAUNCHCOUNT, 1) }
        lifecycleModule.hasLaunched = false

        val data = DataObject.create {
            put("key1", "stringValue1")
            put("key2", "stringValue2")
        }

        lifecycleModule.registerLifecycleEvent(LifecycleEvent.Launch, 1234L, data)

        verify {
            tracker.track(match { dispatch ->
                dispatch.payload().getInt(StateKey.LIFECYCLE_LAUNCHCOUNT) == 1
                        && dispatch.payload().getString("key1") == "stringValue1"
                        && dispatch.payload().getString("key2") == "stringValue2"

            }, any())
        }
    }

    @Test
    fun autoTrackingDisabled_RegistersWakeEvent_MergesCustomDataObject() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Sleep
        every { mockLifecycleService.registerWake(any()) } returns DataObject.create { put(StateKey.LIFECYCLE_WAKECOUNT, 1) }

        val data = DataObject.create {
            put("key3", "stringValue3")
            put("key4", "stringValue4")
        }

        lifecycleModule.registerLifecycleEvent(LifecycleEvent.Wake, 1234L, data)

        verify {
            tracker.track(match { dispatch ->
                dispatch.payload().getInt(StateKey.LIFECYCLE_WAKECOUNT) == 1
                        && dispatch.payload().getString("key3") == "stringValue3"
                        && dispatch.payload().getString("key4") == "stringValue4"
            }, any())
        }
    }

    @Test
    fun autoTrackingDisabled_RegistersSleepEvent_MergesCustomDataObject() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Launch
        every { mockLifecycleService.registerSleep(any()) } returns DataObject.create { put(StateKey.LIFECYCLE_SLEEPCOUNT, 1) }

        val data = DataObject.create {
            put("key5", "stringValue5")
            put("key6", "stringValue6")
        }

        lifecycleModule.registerLifecycleEvent(LifecycleEvent.Sleep, 1234L, data)

        verify {
            tracker.track(match { dispatch ->
                dispatch.payload().getInt(StateKey.LIFECYCLE_SLEEPCOUNT) == 1
                        && dispatch.payload().getString("key5") == "stringValue5"
                        && dispatch.payload().getString("key6") == "stringValue6"
            }, any())
        }
    }

    @Test(expected = InvalidEventOrderException::class)
    fun autoTrackingDisabled_LaunchEvent_ThrowsInvalidOrder_On_LaunchAfterWake() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Wake
        lifecycleModule.hasLaunched = true

        lifecycleModule.registerLifecycleEvent(
            LifecycleEvent.Launch,
            1234L,
            DataObject.EMPTY_OBJECT
        )
    }

    @Test(expected = InvalidEventOrderException::class)
    fun autoTrackingDisabled_SleepEvent_ThrowsInvalidOrder_On_LaunchAfterLaunch() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Launch
        lifecycleModule.hasLaunched = true

        lifecycleModule.launch()
    }

    @Test(expected = InvalidEventOrderException::class)
    fun autoTrackingDisabled_SleepEvent_ThrowsInvalidOrder_On_WakeAfterWake() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Wake
        lifecycleModule.hasLaunched = true

        lifecycleModule.wake()
    }

    @Test(expected = InvalidEventOrderException::class)
    fun autoTrackingDisabled_SleepEvent_ThrowsInvalidOrder_On_WakeAfterLaunch() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Launch
        lifecycleModule.hasLaunched = true

        lifecycleModule.wake()
    }

    @Test(expected = InvalidEventOrderException::class)
    fun autoTrackingDisabled_SleepEvent_ThrowsInvalidOrder_On_SleepAfterSleep() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns LifecycleConfiguration.DEFAULT_TRACKED_EVENTS
        every { mockLifecycleService.lastLifecycleEvent } returns LifecycleEvent.Sleep
        lifecycleModule.hasLaunched = true

        lifecycleModule.sleep()
    }

    @Test
    fun autoTrackingDisabled_NoEventsTracked_WhenConfigurationTrackedEvents_IsEmpty() {
        every { mockConfiguration.autoTrackingEnabled } returns false
        every { mockConfiguration.trackedLifecycleEvents } returns emptyList()
        every { mockLifecycleService.lastLifecycleEvent } returns null andThen LifecycleEvent.Launch andThen LifecycleEvent.Sleep
        lifecycleModule.hasLaunched = false

        lifecycleModule.launch()
        lifecycleModule.sleep()
        lifecycleModule.wake()

        verify(exactly = 0) {
            tracker.track(any(), any())
        }
    }

    @Test(expected = ManualTrackingException::class)
    fun autoTrackingEnabled_throwsException_ForManuallyTracked_LaunchEvent() {
        every { mockConfiguration.autoTrackingEnabled } returns true

        lifecycleModule.launch()
    }

    @Test(expected = ManualTrackingException::class)
    fun autoTrackingEnabled_throwsException_ForManuallyTracked_WakeEvent() {
        every { mockConfiguration.autoTrackingEnabled } returns true

        lifecycleModule.wake()
    }

    @Test(expected = ManualTrackingException::class)
    fun autoTrackingEnabled_throwsException_ForManuallyTracked_SleepEvent() {
        every { mockConfiguration.autoTrackingEnabled } returns true

        lifecycleModule.sleep()
    }

    @Test
    fun autoTrackingEnabled_TracksFirstLaunch_OnInitApplicationStatusSubscription() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        lifecycleModule.hasLaunched = false
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())

        verify { mockLifecycleService.registerLaunch(any()) }
    }

    @Test
    fun autoTrackingEnabled_FirstLaunchSkipSubsequentWake_OnInitApplicationStatusSubscription() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        lifecycleModule.hasLaunched = false

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        verify(exactly = 0) { mockLifecycleService.registerWake(any()) }
    }

    @Test
    fun autoTrackingEnabled_FirstLaunchTracked_OnForegroundedApplicationStatusSubscription() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        lifecycleModule.hasLaunched = false

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        verify { mockLifecycleService.registerLaunch(any()) }
    }

    @Test
    fun autoTrackingEnabled_FirstLaunchAndSleepTracked_OnBackgroundedApplicationStatusSubscription() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        lifecycleModule.hasLaunched = false
        every { mockLifecycleService.lastLifecycleEvent } returns null andThen LifecycleEvent.Launch

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())

        verify {
            mockLifecycleService.registerLaunch(any())
            mockLifecycleService.registerSleep(any())
        }
    }

    @Test(expected = InvalidEventOrderException::class)
    fun autoTrackingEnabled_LaunchAndWakeNotTracked_AfterInitialLaunch() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        lifecycleModule.hasLaunched = false

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        verify(exactly = 1) { mockLifecycleService.registerLaunch(any()) }
        verify(exactly = 0) { mockLifecycleService.registerWake(any()) }
    }

    @Test
    fun autoTrackingEnabled_WakeNotTracked_AfterInitialLaunch() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        lifecycleModule.hasLaunched = false

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        verify(exactly = 1) { mockLifecycleService.registerLaunch(any()) }
        verify(exactly = 0) { mockLifecycleService.registerWake(any()) }
    }

    @Test(expected = InvalidEventOrderException::class)
    fun autoTrackingEnabled_WakeNotTracked_AfterWake() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        every { mockConfiguration.sessionTimeoutInMinutes } returns -1
        lifecycleModule.hasLaunched = false
        every { mockLifecycleService.lastLifecycleEvent } returns null andThen LifecycleEvent.Launch andThen LifecycleEvent.Sleep andThen LifecycleEvent.Wake

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())

        verify(exactly = 1) { mockLifecycleService.registerWake(any()) }
    }

    @Test(expected = InvalidEventOrderException::class)
    fun autoTrackingEnabled_LaunchNotTracked_AfterWake() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        every { mockConfiguration.sessionTimeoutInMinutes } returns 3
        lifecycleModule.hasLaunched = false
        every { mockLifecycleService.lastLifecycleEvent } returns null andThen LifecycleEvent.Launch andThen LifecycleEvent.Sleep andThen LifecycleEvent.Wake

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())

        verify(exactly = 1) {
            mockLifecycleService.registerLaunch(any())
            mockLifecycleService.registerSleep(any())
            mockLifecycleService.registerWake(any())
        }
    }

    @Test
    fun autoTrackingEnabled_LaunchTracked_AfterForegroundOnSessionTimeout() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        every { mockConfiguration.sessionTimeoutInMinutes } returns 10
        lifecycleModule.hasLaunched = false
        every { mockLifecycleService.lastLifecycleEvent } returns null andThen LifecycleEvent.Launch andThen LifecycleEvent.Sleep

        val currentTimeMillis = System.currentTimeMillis()
        val elevenMinutesAfter = currentTimeMillis + (11 * 60 * 1000) // 11 minutes in milliseconds

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded(currentTimeMillis))
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded(elevenMinutesAfter))

        verifyOrder {
            mockLifecycleService.registerLaunch(any())
            mockLifecycleService.registerSleep(any())
            mockLifecycleService.registerLaunch(any())
        }
    }

    @Test
    fun autoTrackingEnabled_NoEventsTracked_WhenConfigurationTrackedEvents_IsEmpty() {
        every { mockConfiguration.autoTrackingEnabled } returns true
        every { mockConfiguration.trackedLifecycleEvents } returns emptyList()
        every { mockLifecycleService.lastLifecycleEvent } returns null andThen LifecycleEvent.Launch andThen LifecycleEvent.Sleep andThen LifecycleEvent.Wake andThen LifecycleEvent.Sleep
        lifecycleModule.hasLaunched = false

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())

        verify(exactly = 0) {
            tracker.track(any(), any())
        }
    }

    @Test
    fun noEventsTracked_WhenConfigurationAutoTracking_IsDisabled() {
        every { mockConfiguration.autoTrackingEnabled } returns false

        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Foregrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Backgrounded())
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())

        verify(exactly = 0) {
            tracker.track(any(), any())
        }
    }

    @Test
    fun collect_ReturnsEmptyDataObject_WhenConfigurationDataTarget_SetTo_LifecycleEventsOnly() {
        every { mockConfiguration.dataTarget } returns LifecycleDataTarget.LifecycleEventsOnly
        val dispatchContext = DispatchContext(DispatchContext.Source.application(), mockk())

        val data = lifecycleModule.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, data)
    }

    @Test
    fun collect_ReturnsEmptyDataObject_WhenConfigurationDataTarget_SetTo_AllEvents_And_SourceIsLifecycleModule() {
        every { mockConfiguration.dataTarget } returns LifecycleDataTarget.AllEvents
        val dispatchContext = DispatchContext(DispatchContext.Source.module(LifecycleModule::class.java), mockk())

        val data = lifecycleModule.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, data)
    }

    @Test
    fun collect_CallsGetCurrentState_WhenConfigurationDataTarget_SetTo_AllEvents() {
        every { mockConfiguration.dataTarget } returns LifecycleDataTarget.AllEvents
        every { mockLifecycleService.getCurrentState(any()) } returns DataObject.create {
            put(StateKey.LIFECYCLE_TOTALLAUNCHCOUNT, 10)
            put(StateKey.LIFECYCLE_TOTALSLEEPCOUNT, 11)
            put(StateKey.LIFECYCLE_TOTALWAKECOUNT, 12)
        }
        val dispatchContext = DispatchContext(DispatchContext.Source.application(), mockk())

        val data = lifecycleModule.collect(dispatchContext)

        assertEquals(10, data.getInt(StateKey.LIFECYCLE_TOTALLAUNCHCOUNT))
        assertEquals(11, data.getInt(StateKey.LIFECYCLE_TOTALSLEEPCOUNT))
        assertEquals(12, data.getInt(StateKey.LIFECYCLE_TOTALWAKECOUNT))
    }

    @Test
    fun onShutDown_NullifiesActivitiesSubscription() {
        lifecycleModule.activitiesSubscription = applicationStatus.subscribe { }
        lifecycleModule.onShutdown()

        assertNull(lifecycleModule.activitiesSubscription)
    }
}
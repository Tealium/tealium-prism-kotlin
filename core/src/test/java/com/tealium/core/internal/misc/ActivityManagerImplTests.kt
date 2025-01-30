package com.tealium.core.internal.misc

import android.app.Activity
import android.app.Application
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.pubsub.Observer
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ActivityManagerImplTests {

    @RelaxedMockK
    private lateinit var mockApp: Application

    @MockK
    private lateinit var activity1: Activity

    @MockK
    private lateinit var activity2: Activity

    private var timeout: Long = 1L

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { activity1.isChangingConfigurations } returns false
        every { activity2.isChangingConfigurations } returns false
    }

    @Test
    fun activityManagerImpl_BuffersAppStatusInitially() {
        val observer = mockk<Observer<ActivityManager.ApplicationStatus>>(relaxed = true)
        val activityCallbacks = ActivityManagerImpl.ActivityCallbacks(mockApp)
        val activityManager =
            ActivityManagerImpl(
                mockApp,
                mainScheduler = testTealiumScheduler,
                activityMonitor = activityCallbacks,
            )

        activityCallbacks.onActivityResumed(activity1)
        activityCallbacks.onActivityStopped(activity1)
        activityCallbacks.onActivityResumed(activity2)
        activityCallbacks.onActivityStopped(activity2)

        activityManager.applicationStatus.subscribe(observer)

        verify(timeout = 1000) {
            observer.onNext(match { it is ActivityManager.ApplicationStatus.Init })
            observer.onNext(match { it is ActivityManager.ApplicationStatus.Foregrounded })
            observer.onNext(match { it is ActivityManager.ApplicationStatus.Backgrounded })
            observer.onNext(match { it is ActivityManager.ApplicationStatus.Foregrounded })
            observer.onNext(match { it is ActivityManager.ApplicationStatus.Backgrounded })
        }
    }

    @Test
    fun activityManagerImpl_EmitsLastBufferedAppStatus_When_OutOfTime() {
        val observer = mockk<Observer<ActivityManager.ApplicationStatus>>(relaxed = true)
        val activityCallbacks = ActivityManagerImpl.ActivityCallbacks(mockApp)
        val activityManager =
            ActivityManagerImpl(
                mockApp,
                mainScheduler = testTealiumScheduler,
                activityMonitor = activityCallbacks,
                timeoutSeconds = timeout
            )

        activityCallbacks.onActivityResumed(activity1)
        activityCallbacks.onActivityStopped(activity1)
        activityCallbacks.onActivityResumed(activity2)
        activityCallbacks.onActivityStopped(activity2)

        Thread.sleep(TimeUnit.SECONDS.toMillis(timeout) + 10L)

        activityManager.applicationStatus.subscribe(observer)
        verify(exactly = 1) {
            observer.onNext(match { it is ActivityManager.ApplicationStatus.Backgrounded })
        }
        confirmVerified(observer)
    }

    @Test
    fun activityManagerImpl_BuffersActivityStatusInitially() {
        val observer = mockk<Observer<ActivityManager.ActivityStatus>>(relaxed = true)
        val activityCallbacks = ActivityManagerImpl.ActivityCallbacks(
            mockApp,
        )
        val activityManager =
            ActivityManagerImpl(
                mockApp,
                mainScheduler = testTealiumScheduler,
                activityMonitor = activityCallbacks,
            )

        activityCallbacks.onActivityResumed(activity1)
        activityCallbacks.onActivityStopped(activity1)
        activityCallbacks.onActivityResumed(activity2)
        activityCallbacks.onActivityStopped(activity2)

        activityManager.activities.subscribe(observer)

        verify(timeout = 1000) {
            observer.onNext(match { activityStatus ->
                activityStatus.activity == activity1
                        && activityStatus.type == ActivityManager.ActivityLifecycleType.Resumed
            })
            observer.onNext(match { activityStatus ->
                activityStatus.activity == activity1
                        && activityStatus.type == ActivityManager.ActivityLifecycleType.Stopped
            })

            observer.onNext(match { activityStatus ->
                activityStatus.activity == activity2
                        && activityStatus.type == ActivityManager.ActivityLifecycleType.Resumed
            })
            observer.onNext(match { activityStatus ->
                activityStatus.activity == activity2
                        && activityStatus.type == ActivityManager.ActivityLifecycleType.Stopped
            })
        }
    }

    @Test
    fun activityManagerImpl_DoesNotEmitBufferedActivityStatuses_When_OutOfTime() {
        val observer = mockk<Observer<ActivityManager.ActivityStatus>>(relaxed = true)
        val activityCallbacks = ActivityManagerImpl.ActivityCallbacks(mockApp)
        val activityManager =
            ActivityManagerImpl(
                mockApp,
                mainScheduler = testTealiumScheduler,
                activityMonitor = activityCallbacks,
                timeoutSeconds = timeout
            )

        activityCallbacks.onActivityResumed(activity1)
        activityCallbacks.onActivityStopped(activity1)

        Thread.sleep(TimeUnit.SECONDS.toMillis(timeout) + 10L)

        activityManager.activities.subscribe(observer)

        verify {
            observer wasNot Called
        }
        confirmVerified(observer)
    }
}
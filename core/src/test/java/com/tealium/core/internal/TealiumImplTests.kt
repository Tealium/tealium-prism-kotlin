package com.tealium.core.internal

import android.app.Activity
import android.app.Application
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.modules.Module
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.internal.misc.ActivityManagerImpl
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.testSchedulers
import io.mockk.Ordering
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TealiumImplTests {

    lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
    }

    @Test
    fun shutdown_Shuts_Down_Module_Manager() {
        val moduleManager = spyk(ModuleManagerImpl(listOf(), testSchedulers.tealium))
        val tealium =
            TealiumImpl(getDefaultConfig(app), testSchedulers, moduleManager = moduleManager)

        tealium.shutdown()

        verify { moduleManager.shutdown() }
    }

    @Test
    fun tealium_Notifies_ApplicationSubscriberModules_IfSubscribed_AtLaunch() {
        val appObserver = mockk<Observer<ActivityManager.ApplicationStatus>>(relaxed = true)
        val appAwareModuleFactory = TestModuleFactory("app-aware-module") { ctx, _ ->
            ObserverModule(
                ctx.activityManager.applicationStatus,
                appObserver
            )
        }

        val activityCallbacks = ActivityManagerImpl.ActivityCallbacks(app)
        val activityManager = ActivityManagerImpl(
            app,
            mainScheduler = testSchedulers.main,
            activityMonitor = activityCallbacks
        )
        val activity = mockk<Activity>()
        every { activity.isChangingConfigurations } returns false

        // pre-load an init foregrounded, and backgrounded event
        activityCallbacks.onActivityResumed(activity)
        activityCallbacks.onActivityStopped(activity)

        testSchedulers.tealium.execute {
            TealiumImpl(
                getDefaultConfig(app, modules = listOf(appAwareModuleFactory)),
                testSchedulers,
                activityManager = activityManager
            )
        }

        verify(timeout = 2500, ordering = Ordering.ORDERED) {
            appObserver.onNext(match { it is ActivityManager.ApplicationStatus.Init })
            appObserver.onNext(match { it is ActivityManager.ApplicationStatus.Foregrounded })
            appObserver.onNext(match { it is ActivityManager.ApplicationStatus.Backgrounded })
        }
    }

    @Test
    fun tealium_Notifies_ActivitySubscriberModules_IfSubscribed_AtLaunch() {
        val appObserver = mockk<Observer<ActivityManager.ActivityStatus>>(relaxed = true)
        val appAwareModuleFactory = TestModuleFactory("activity-aware-module") { ctx, _ ->
            ObserverModule(
                ctx.activityManager.activities,
                appObserver
            )
        }

        val activityCallbacks = ActivityManagerImpl.ActivityCallbacks(app)
        val activityManager = ActivityManagerImpl(
            app,
            mainScheduler = testSchedulers.main,
            activityMonitor = activityCallbacks
        )
        val activity = mockk<Activity>()
        every { activity.isChangingConfigurations } returns false

        // pre-load an init foregrounded, and backgrounded event
        activityCallbacks.onActivityResumed(activity)
        activityCallbacks.onActivityStopped(activity)

        testSchedulers.tealium.execute {
            TealiumImpl(
                getDefaultConfig(app, modules = listOf(appAwareModuleFactory)),
                testSchedulers,
                activityManager = activityManager
            )
        }

        verify(timeout = 2500, ordering = Ordering.ORDERED) {
            appObserver.onNext(match {
                it.activity == activity
                        && it.type == ActivityManager.ActivityLifecycleType.Resumed
            })
            appObserver.onNext(match {
                it.activity == activity
                        && it.type == ActivityManager.ActivityLifecycleType.Stopped
            })
        }
    }

    private class ObserverModule<T>(
        observable: Observable<T>,
        private val observer: Observer<T>,
        override val id: String = "observer",
        override val version: String = "1.0.0"
    ) : Module, Observer<T> by observer {
        init {
            observable.subscribe(this)
        }
    }
}
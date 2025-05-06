package com.tealium.core.internal.modules.deeplink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleManager
import com.tealium.core.api.modules.ModuleNotEnabledException
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.persistence.DataStore
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.persistence.ModuleStoreProvider
import com.tealium.core.api.persistence.PersistenceException
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.ReplaySubject
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.core.api.settings.DeepLinkSettingsBuilder
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.tracking.DispatchContext
import com.tealium.core.api.tracking.TrackResult
import com.tealium.core.api.tracking.TrackResultListener
import com.tealium.core.api.tracking.Tracker
import com.tealium.core.internal.modules.ModuleManagerImpl
import com.tealium.core.internal.modules.trace.TraceManagerModule
import com.tealium.tests.common.SynchronousScheduler
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.buildConfiguration
import com.tealium.tests.common.mockkEditor
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinkHandlerModuleTests {

    @RelaxedMockK
    lateinit var dataStore: DataStore

    @RelaxedMockK
    lateinit var tracker: Tracker

    @RelaxedMockK
    lateinit var traceModule: TraceManagerModule

    @RelaxedMockK
    lateinit var dataStoreEditor: DataStore.Editor

    private lateinit var moduleManager: ModuleManager
    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var activities: ReplaySubject<ActivityManager.ActivityStatus>
    private lateinit var uri: Uri
    private lateinit var configuration: DeepLinkHandlerConfiguration
    private lateinit var deepLinkHandlerModule: DeepLinkHandlerModule

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        configuration = DeepLinkHandlerConfiguration(
            automaticDeepLinkTracking = true,
            sendDeepLinkEvent = true,
            deepLinkTraceEnabled = true
        )
        modules = Observables.stateSubject(listOf(traceModule))
        moduleManager = ModuleManagerImpl(SynchronousScheduler(), modules)

        dataStoreEditor = mockkEditor()
        every { dataStore.edit() } returns dataStoreEditor

        activities = Observables.replaySubject(1)

        uri = Uri.parse("https://example.com/path?param1=value1&param2=value2")

        deepLinkHandlerModule = DeepLinkHandlerModule(
            dataStore,
            tracker,
            moduleManager,
            activities,
            configuration,
            SystemLogger
        )
    }

    @Test
    fun collect_Returns_Empty_Object_When_Source_Is_From_Module() {
        val dispatchContext = DispatchContext(
            DispatchContext.Source.module(DeepLinkHandlerModule::class.java),
            DataObject.EMPTY_OBJECT
        )

        val result = deepLinkHandlerModule.collect(dispatchContext)

        assertEquals(DataObject.EMPTY_OBJECT, result)
    }

    @Test
    fun collect_Returns_DataStore_Contents_When_Source_Is_Not_From_Module() {
        val dispatchContext = DispatchContext(
            DispatchContext.Source.application(),
            DataObject.EMPTY_OBJECT
        )
        val dataObject = DataObject.create { put("key", "value") }
        every { dataStore.getAll() } returns dataObject

        val result = deepLinkHandlerModule.collect(dispatchContext)

        assertEquals(dataObject, result)
    }

    @Test
    fun handle_Does_Nothing_When_Uri_Is_Opaque() {
        uri = Uri.parse("mailto:someone@test.com")

        deepLinkHandlerModule.handle(uri)

        verify(exactly = 0) {
            dataStore.edit()
            tracker.track(any(), any())
        }
    }

    @Test
    fun handle_Removes_Existing_Data_And_Stores_DeepLink_Data_When_Uri_Is_Valid() {
        deepLinkHandlerModule.handle(uri)

        val expected = DataObject.create {
            put(Dispatch.Keys.DEEP_LINK_URL, uri.toString())
            put("${Dispatch.Keys.DEEP_LINK_QUERY_PREFIX}_param1", "value1")
            put("${Dispatch.Keys.DEEP_LINK_QUERY_PREFIX}_param2", "value2")
        }
        verify {
            dataStoreEditor.clear()
            dataStoreEditor.putAll(match { it == expected }, Expiry.SESSION)
            dataStoreEditor.commit()
        }
    }

    @Test
    fun handle_Tracks_Event_When_SendDeepLinkEvent_Is_Enabled() {
        deepLinkHandlerModule.handle(uri)

        verify {
            tracker.track(match {
                it.tealiumEvent == DeepLinkHandlerModule.DEEP_LINK_EVENT
            }, any(), any())
        }
    }

    @Test
    fun handle_Does_Not_Track_Event_When_SendDeepLinkEvent_Is_Disabled() {
        val configuration = DeepLinkSettingsBuilder()
            .setSendDeepLinkEventEnabled(false)
            .buildConfiguration()
        deepLinkHandlerModule.updateConfiguration(configuration)

        deepLinkHandlerModule.handle(uri)

        verify(exactly = 0) {
            tracker.track(any(), any())
        }
    }

    @Test
    fun handle_Includes_DeepLink_Url_In_DataObject() {
        deepLinkHandlerModule.handle(uri)

        verify {
            dataStoreEditor.putAll(match {
                it.getString(Dispatch.Keys.DEEP_LINK_URL) == uri.toString()
            }, Expiry.SESSION)
        }
    }

    @Test
    fun handle_Still_Includes_DeepLink_Url_In_DataObject_When_Automatic_Tracking_Disabled() {
        val configuration = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(false)
            .buildConfiguration()
        deepLinkHandlerModule.updateConfiguration(configuration)

        deepLinkHandlerModule.handle(uri)

        verify {
            dataStoreEditor.putAll(match {
                it.getString(Dispatch.Keys.DEEP_LINK_URL) == uri.toString()
            }, Expiry.SESSION)
        }
    }

    @Test
    fun handle_Includes_Query_Parameters_In_DataObject() {
        deepLinkHandlerModule.handle(uri)

        verify {
            dataStoreEditor.putAll(match {
                it.getString("${Dispatch.Keys.DEEP_LINK_QUERY_PREFIX}_param1") == "value1"
            }, Expiry.SESSION)
            dataStoreEditor.putAll(match {
                it.getString("${Dispatch.Keys.DEEP_LINK_QUERY_PREFIX}_param2") == "value2"
            }, Expiry.SESSION)
        }
    }

    @Test
    fun handle_Includes_Referrer_Url_In_DataObject_When_Provided() {
        val referrer = Uri.parse("http://referrer.com")

        deepLinkHandlerModule.handle(uri, referrer)

        verify {
            dataStoreEditor.putAll(match {
                it.getString(Dispatch.Keys.DEEP_LINK_REFERRER_URL) == "http://referrer.com"
            }, Expiry.SESSION)
        }
    }

    @Test
    fun handle_Calls_TraceModule_Join_When_TraceId_Present() {
        val traceId = "test_trace_id"
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkHandlerModule.TRACE_ID_QUERY_PARAM, traceId)
            .build()

        deepLinkHandlerModule.handle(uri)

        verify {
            traceModule.join(traceId)
        }
    }

    @Test
    fun handle_Does_Not_Call_TraceModule_Join_When_TraceId_Present() {
        val configuration = DeepLinkSettingsBuilder()
            .setDeepLinkTraceEnabled(false)
            .buildConfiguration()
        deepLinkHandlerModule.updateConfiguration(configuration)
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkHandlerModule.TRACE_ID_QUERY_PARAM, "traceId")
            .build()

        deepLinkHandlerModule.handle(uri)

        verify(exactly = 0) {
            traceModule.join("traceId")
        }
    }

    @Test
    fun handle_Calls_TraceModule_Leave_When_LeaveTrace_Present() {
        val traceId = "test_trace_id"
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkHandlerModule.TRACE_ID_QUERY_PARAM, traceId)
            .appendQueryParameter(DeepLinkHandlerModule.LEAVE_TRACE_QUERY_PARAM, "true")
            .build()

        deepLinkHandlerModule.handle(uri)

        verify {
            traceModule.leave()
        }
    }

    @Test
    fun handle_Calls_TraceModule_KillVisitorSession_When_KillVisitorSession_Present() {
        val traceId = "test_trace_id"
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkHandlerModule.TRACE_ID_QUERY_PARAM, traceId)
            .appendQueryParameter(DeepLinkHandlerModule.KILL_VISITOR_SESSION, "true")
            .build()

        deepLinkHandlerModule.handle(uri)

        verify {
            traceModule.killVisitorSession(any())
        }
    }

    @Test(expected = ModuleNotEnabledException::class)
    fun handle_Trace_Throws_ModuleNotEnabledException_When_TraceModule_Not_Available() {
        modules.onNext(emptyList()) // disable trace
        val traceId = "test_trace_id"
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkHandlerModule.TRACE_ID_QUERY_PARAM, traceId)
            .appendQueryParameter(DeepLinkHandlerModule.KILL_VISITOR_SESSION, "true")
            .build()

        deepLinkHandlerModule.handle(uri)
    }

    @Test
    fun handle_Trace_Throws_Exception_When_KillVisitorSession_Fails() {
        val callback = slot<TrackResultListener>()
        every { traceModule.killVisitorSession(capture(callback)) } answers {
            callback.captured.onTrackResultReady(TrackResult.Dropped(mockk()) )
        }
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkHandlerModule.TRACE_ID_QUERY_PARAM, "test_trace_id")
            .appendQueryParameter(DeepLinkHandlerModule.KILL_VISITOR_SESSION, "true")
            .build()

        deepLinkHandlerModule.handle(uri)
    }

    @Test(expected = PersistenceException::class)
    fun handle_DeepLink_Throws_Exception_When_Persistence_Fails() {
        every { dataStoreEditor.commit() } throws PersistenceException("", mockk())

        deepLinkHandlerModule.handle(uri)
    }

    @Test
    fun onActivityStatus_Handles_DeepLink_When_Activity_Created_With_ViewIntent() {
        val activity = mockActivity(Intent.ACTION_VIEW)
        val activityStatus = ActivityManager.ActivityStatus(
            activity = activity,
            type = ActivityManager.ActivityLifecycleType.Created
        )

        activities.onNext(activityStatus)

        verify {
            dataStoreEditor.putAll(any(), Expiry.SESSION)
            dataStoreEditor.commit()
        }
    }

    @Test
    fun onActivityStatus_Adds_Referrer_When_Referrer_Available() {
        val activity = mockActivity(referrer = Uri.parse("com.example.referrer"))
        val activityStatus = ActivityManager.ActivityStatus(
            activity = activity,
            type = ActivityManager.ActivityLifecycleType.Created
        )

        activities.onNext(activityStatus)

        verify {
            dataStoreEditor.putAll(
                match { it.getString(Dispatch.Keys.DEEP_LINK_REFERRER_URL) == "com.example.referrer" },
                Expiry.SESSION
            )
            dataStoreEditor.commit()
        }
    }

    @Test
    fun onActivityStatus_Does_Not_Add_Referrer_When_No_Referrer_Info() {
        val activity = mockActivity(referrer = null)
        val activityStatus = ActivityManager.ActivityStatus(
            activity = activity,
            type = ActivityManager.ActivityLifecycleType.Created
        )

        activities.onNext(activityStatus)

        verify {
            dataStoreEditor.putAll(
                match { it.getString(Dispatch.Keys.DEEP_LINK_REFERRER_URL) == null },
                Expiry.SESSION
            )
            dataStoreEditor.commit()
        }
    }

    @Test
    fun onActivityStatus_Does_Nothing_When_Activity_Not_Created() {
        val activity = mockActivity(Intent.ACTION_VIEW)
        val activityStatus = ActivityManager.ActivityStatus(
            activity = activity,
            type = ActivityManager.ActivityLifecycleType.Resumed
        )

        activities.onNext(activityStatus)

        verify(inverse = true) {
            dataStoreEditor.putAll(any(), Expiry.SESSION)
            dataStoreEditor.commit()
        }
    }

    @Test
    fun onActivityStatus_Does_Nothing_When_Intent_Not_ViewAction() {
        val activity = mockActivity(Intent.ACTION_DIAL)
        val activityStatus = ActivityManager.ActivityStatus(
            activity = activity,
            type = ActivityManager.ActivityLifecycleType.Created
        )

        activities.onNext(activityStatus)

        verify(inverse = true) {
            dataStoreEditor.putAll(any(), Expiry.SESSION)
            dataStoreEditor.commit()
        }
    }

    @Test
    fun updateConfiguration_Updates_Configuration_And_Subscription() {
        val disposable = mockk<Disposable>(relaxed = true)
        activities = mockk()
        every { activities.subscribe(any()) } returns disposable

        // Start with automatic tracking enabled
        deepLinkHandlerModule = DeepLinkHandlerModule(
            dataStore,
            tracker,
            moduleManager,
            activities,
            DeepLinkHandlerConfiguration(automaticDeepLinkTracking = true),
            SystemLogger
        )

        // Update to disable automatic tracking
        val newConfig = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(false)
            .buildConfiguration()

        deepLinkHandlerModule.updateConfiguration(newConfig)

        // Should dispose the subscription
        verify {
            disposable.dispose()
        }

        // Update to re-enable automatic tracking
        val enabledConfig = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(true)
            .buildConfiguration()

        deepLinkHandlerModule.updateConfiguration(enabledConfig)

        // Should create a new subscription
        verify(exactly = 2) {
            activities.subscribe(any())
        }
    }

    @Test
    fun onShutdown_Disposes_Activity_Subscription() {
        val disposable = mockk<Disposable>(relaxed = true)
        activities = mockk()
        every { activities.subscribe(any()) } returns disposable

        deepLinkHandlerModule = DeepLinkHandlerModule(
            dataStore,
            tracker,
            moduleManager,
            activities,
            DeepLinkHandlerConfiguration(automaticDeepLinkTracking = true),
            SystemLogger
        )

        deepLinkHandlerModule.onShutdown()

        // Should dispose the subscription
        verifyOrder {
            activities.subscribe(any())
            disposable.dispose()
        }
    }

    @Test
    fun factory_Id_Returns_Expected_Module_Id() {
        val factory = DeepLinkHandlerModule.Factory(DataObject.EMPTY_OBJECT)

        assertEquals(DeepLinkHandlerModule.MODULE_ID, factory.id)
    }

    @Test
    fun factory_GetEnforcedSettings_Returns_Provided_Settings() {
        val enforcedSettings = DataObject.create {
            put(DeepLinkHandlerConfiguration.KEY_AUTOMATIC_DEEPLINK_TRACKING, false)
            put(DeepLinkHandlerConfiguration.KEY_SEND_DEEPLINK_EVENT, true)
        }

        val factory = DeepLinkHandlerModule.Factory(enforcedSettings)

        assertEquals(enforcedSettings, factory.getEnforcedSettings())
    }

    @Test
    fun factory_Constructor_With_DeepLinkSettingsBuilder_Creates_Factory_With_Builder_Settings() {
        val settingsBuilder = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(false)
        val expected = settingsBuilder.build()

        val factory = DeepLinkHandlerModule.Factory(settingsBuilder)

        assertEquals(expected, factory.getEnforcedSettings())
    }

    @Test
    fun factory_Create_Returns_DeepLinkHandlerModule_With_Configuration() {
        val factory = DeepLinkHandlerModule.Factory(DataObject.EMPTY_OBJECT)
        val tealiumContext = mockk<TealiumContext>()
        val storageProvider = mockk<ModuleStoreProvider>()

        every { tealiumContext.storageProvider } returns storageProvider
        every { tealiumContext.tracker } returns tracker
        every { tealiumContext.moduleManager } returns moduleManager
        every { tealiumContext.activityManager.activities } returns activities
        every { tealiumContext.logger } returns SystemLogger
        every { storageProvider.getModuleStore(factory) } returns dataStore

        val configuration = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(true)
            .buildConfiguration()

        val module = factory.create(tealiumContext, configuration)

        assertNotNull(module)
        assertTrue(module is DeepLinkHandlerModule)
        assertEquals(DeepLinkHandlerModule.MODULE_ID, module?.id)
    }

    private fun mockActivity(
        action: String = Intent.ACTION_VIEW,
        uri: Uri = this.uri,
        referrer: Uri? = null
    ): Activity {
        val activity = mockk<Activity>()
        val intent = mockk<Intent>()

        every { activity.intent } returns intent
        every { activity.referrer } returns referrer
        every { intent.action } returns action
        every { intent.data } returns uri
        every { intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri::class.java) } returns referrer
        every {
            intent.getParcelableExtra(
                Intent.EXTRA_REFERRER_NAME,
                String::class.java
            )
        } returns referrer?.toString()

        return activity
    }
}
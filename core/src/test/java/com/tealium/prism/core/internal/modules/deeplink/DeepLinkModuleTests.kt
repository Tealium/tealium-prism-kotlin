package com.tealium.prism.core.internal.modules.deeplink

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleShutdownException
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.persistence.ModuleStoreProvider
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.settings.modules.DeepLinkSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.api.tracking.TrackResultListener
import com.tealium.prism.core.api.tracking.Tracker
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.trace.TraceModule
import com.tealium.prism.core.internal.modules.enableModuleSettings
import com.tealium.prism.core.internal.modules.mockReady
import com.tealium.tests.common.ModuleSettingRow
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestModuleFactory
import com.tealium.tests.common.buildConfiguration
import com.tealium.tests.common.mockkEditor
import com.tealium.tests.common.settingsPass
import io.mockk.MockKAnnotations
import io.mockk.MockKMatcherScope
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
class DeepLinkModuleTests {

    @RelaxedMockK
    lateinit var dataStore: DataStore

    @RelaxedMockK
    lateinit var tracker: Tracker

    @RelaxedMockK
    lateinit var traceModule: TraceModule

    @RelaxedMockK
    lateinit var dataStoreEditor: DataStore.Editor

    private lateinit var moduleManager: ModuleManager
    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var activities: ReplaySubject<ActivityManager.ActivityStatus>
    private lateinit var uri: Uri
    private lateinit var configuration: DeepLinkModuleConfiguration
    private lateinit var deepLinkModule: DeepLinkModule

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        configuration = DeepLinkModuleConfiguration(
            automaticDeepLinkTracking = true,
            sendDeepLinkEvent = true,
            deepLinkTraceEnabled = true
        )
        modules = Observables.stateSubject(listOf(traceModule))
        moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS, modules)

        dataStoreEditor = mockkEditor()
        every { dataStore.edit() } returns dataStoreEditor

        activities = Observables.replaySubject(1)

        uri = Uri.parse("https://example.com/path?param1=value1&param2=value2")

        deepLinkModule = createDeepLinkModule()
    }

    @Test
    fun collect_Returns_Empty_Object_When_Source_Is_From_Module() {
        val dispatchContext = DispatchContext(
            DispatchContext.Source.module(DeepLinkModule::class.java),
            DataObject.EMPTY_OBJECT
        )

        val result = deepLinkModule.collect(dispatchContext)

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

        val result = deepLinkModule.collect(dispatchContext)

        assertEquals(dataObject, result)
    }

    @Test
    fun handle_Reports_Success_And_Does_Nothing_When_Uri_Is_Opaque() {
        uri = Uri.parse("mailto:someone@test.com")
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        deepLinkModule.handle(uri, callback = callback)

        verify(exactly = 0) {
            dataStore.edit()
            tracker.track(any(), any())
        }
        verify(exactly = 1) {
            callback.onComplete(match { it.isSuccess })
        }
    }

    @Test
    fun handle_Removes_Existing_Data_And_Stores_DeepLink_Data_When_Uri_Is_Valid() {
        deepLinkModule.handle(uri)

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
    fun handle_Reports_Success_When_No_TraceId_Present() {
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        deepLinkModule.handle(uri, callback = callback)

        verify(exactly = 1) {
            callback.onComplete(match { it.isSuccess })
        }
    }

    @Test
    fun handle_Tracks_Event_When_SendDeepLinkEvent_Is_Enabled() {
        deepLinkModule.handle(uri)

        verify {
            tracker.track(match {
                it.tealiumEvent == DeepLinkModule.DEEP_LINK_EVENT
            }, any(), any())
        }
    }

    @Test
    fun handle_Does_Not_Track_Event_When_SendDeepLinkEvent_Is_Disabled() {
        val configuration = DeepLinkSettingsBuilder()
            .setSendDeepLinkEventEnabled(false)
            .buildConfiguration()
        deepLinkModule.updateConfiguration(configuration)

        deepLinkModule.handle(uri)

        verify(exactly = 0) {
            tracker.track(any(), any())
        }
    }

    @Test
    fun handle_Includes_DeepLink_Url_In_DataObject() {
        deepLinkModule.handle(uri)

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
        deepLinkModule.updateConfiguration(configuration)

        deepLinkModule.handle(uri)

        verify {
            dataStoreEditor.putAll(match {
                it.getString(Dispatch.Keys.DEEP_LINK_URL) == uri.toString()
            }, Expiry.SESSION)
        }
    }

    @Test
    fun handle_Includes_Query_Parameters_In_DataObject() {
        deepLinkModule.handle(uri)

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

        deepLinkModule.handle(uri, referrer)

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
            .appendQueryParameter(DeepLinkModule.TRACE_ID_QUERY_PARAM, traceId)
            .build()

        deepLinkModule.handle(uri)

        verify {
            traceModule.join(traceId)
        }
    }

    @Test
    fun handle_Does_Not_Call_TraceModule_Join_When_Trace_Disabled() {
        val configuration = DeepLinkSettingsBuilder()
            .setDeepLinkTraceEnabled(false)
            .buildConfiguration()
        deepLinkModule.updateConfiguration(configuration)
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkModule.TRACE_ID_QUERY_PARAM, "traceId")
            .build()

        deepLinkModule.handle(uri)

        verify(exactly = 0) {
            traceModule.join("traceId")
        }
    }

    @Test
    fun handle_Calls_TraceModule_Leave_When_LeaveTrace_Present() {
        val traceId = "test_trace_id"
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkModule.TRACE_ID_QUERY_PARAM, traceId)
            .appendQueryParameter(DeepLinkModule.LEAVE_TRACE_QUERY_PARAM, "true")
            .build()

        deepLinkModule.handle(uri)

        verify {
            traceModule.leave()
        }
    }

    @Test
    fun handle_Calls_TraceModule_ForceEndVisit_When_ForceEndVisit_Present() {
        val traceId = "test_trace_id"
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkModule.TRACE_ID_QUERY_PARAM, traceId)
            .appendQueryParameter(DeepLinkModule.FORCE_END_OF_VISIT, "true")
            .build()

        deepLinkModule.handle(uri)

        verify {
            traceModule.forceEndOfVisit(any())
        }
    }

    @Test
    fun handle_Trace_Reports_ModuleNotEnabledException_When_TraceModule_Not_Available() {
        modules.onNext(emptyList()) // disable trace
        val traceId = "test_trace_id"
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkModule.TRACE_ID_QUERY_PARAM, traceId)
            .appendQueryParameter(DeepLinkModule.FORCE_END_OF_VISIT, "true")
            .build()

        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)
        deepLinkModule.handle(uri, callback = callback)

        verify {
            callback.onComplete(match { it.exceptionOrNull() is ModuleNotEnabledException })
        }
    }

    @Test
    fun handle_Trace_Does_Not_Persist_Or_Track_DeepLink_When_TraceModule_Not_Available() {
        modules.onNext(emptyList()) // disable trace
        val traceId = "test_trace_id"
        uri = uri.buildUpon()
            .appendQueryParameter(DeepLinkModule.TRACE_ID_QUERY_PARAM, traceId)
            .build()

        deepLinkModule.handle(uri)

        verify(exactly = 0) {
            dataStoreEditor.commit()
            tracker.track(any(), any(), any())
        }
    }

    @Test
    fun handle_Trace_Joins_Before_Persisting_DeepLink() {
        val traceId = "test_trace_id"

        deepLinkModule.handle(traceUri(traceId))

        // the deep_link event has to land inside the joined trace
        verifyOrder {
            traceModule.join(traceId)
            dataStoreEditor.commit()
        }
    }

    @Test
    fun handle_Trace_Joins_When_Url_Arrives_Before_The_First_Settings_Pass() {
        // empty seed: the module system has not finished a settings pass yet
        val freshManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS, Observables.stateSubject(listOf()))
        freshManager.addModuleFactory(
            TestModuleFactory.singleInstance(Modules.Types.TRACE) { _, _, _ -> traceModule }
        )
        val freshDeepLinkModule = createDeepLinkModule(moduleManager = freshManager)
        val traceId = "test_trace_id"
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        freshDeepLinkModule.handle(traceUri(traceId), callback = callback)

        // nothing may resolve while the trace module can't be looked up yet
        verify(exactly = 0) {
            traceModule.join(any())
            dataStoreEditor.commit()
            callback.onComplete(any())
        }

        // the first settings pass completes: only now is the lookup answered
        val context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger
        freshManager.updateModuleSettings(context, enableModuleSettings(Modules.Types.TRACE))

        // the deep_link event has to land inside the joined trace
        verifyOrder {
            traceModule.join(traceId)
            dataStoreEditor.commit()
        }
        verify(exactly = 1) {
            callback.onComplete(match { it.isSuccess })
        }
    }

    @Test
    fun handle_Trace_Joins_When_Url_Arrives_Between_Two_Settings_Passes() {
        // empty seed: the module system has not finished a settings pass yet
        val freshManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS, Observables.stateSubject(listOf()))
        val traceId = "test_trace_id"
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)
        freshManager.addModuleFactory(
            TestModuleFactory.singleInstance(Modules.Types.TRACE) { _, _, _ -> traceModule }
        )
        // the link arrives on creation: ordered before Trace, so the trace module does not exist
        // yet, and the lookup subscribes while the pass that creates both is still running
        freshManager.addModuleFactory(
            TestModuleFactory.singleInstance(Modules.Types.DEEP_LINK) { _, _, _ ->
                createDeepLinkModule(moduleManager = freshManager).also { module ->
                    module.handle(traceUri(traceId), callback = callback)
                }
            }
        )
        val context = mockk<TealiumContext>()
        every { context.logger } returns SystemLogger

        // first pass: neither Trace nor DeepLink is configured
        freshManager.mockReady(context)
        // second pass: DeepLink and Trace arrive together, the way remote settings deliver them
        freshManager.updateModuleSettings(
            context,
            settingsPass(
                ModuleSettingRow(Modules.Types.DEEP_LINK, order = 0),
                ModuleSettingRow(Modules.Types.TRACE, order = 1)
            )
        )

        // the settled answer of the first pass (no trace module) must not be replayed mid-pass:
        // that would report ModuleNotEnabledException instead of joining
        verify(exactly = 0) {
            callback.onComplete(match { it.isFailure })
        }
        verify(exactly = 1) {
            traceModule.join(traceId)
            callback.onComplete(match { it.isSuccess })
        }
    }

    @Test
    fun handle_Trace_Joins_And_Succeeds_When_ForceEndVisit_Is_Dropped() {
        val traceId = "test_trace_id"
        val trackResult = slot<TrackResultListener>()
        every { traceModule.forceEndOfVisit(capture(trackResult)) } answers {
            trackResult.captured.onTrackResultReady(TrackResult.dropped(mockk(), ""))
        }
        val link = traceUri(traceId).buildUpon()
            .appendQueryParameter(DeepLinkModule.FORCE_END_OF_VISIT, "true")
            .build()
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        deepLinkModule.handle(link, callback = callback)

        // a dropped forceEndOfVisit only logs a warning - the trace is still joined
        verify {
            traceModule.join(traceId)
            dataStoreEditor.commit()
        }
        verify(exactly = 1) {
            callback.onComplete(match { it.isSuccess })
        }
    }

    @Test
    fun handle_DeepLink_Reports_Failure_When_Persistence_Fails() {
        // a real cause, not mockk(): a mocked cause makes the failure report unreadable, and a
        // genuinely failing test then shows as passing
        val persistenceFailure = PersistenceException("", Exception())
        every { dataStoreEditor.commit() } throws persistenceFailure
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        // reported through the callback, never propagated
        deepLinkModule.handle(uri, callback = callback)

        verify(exactly = 1) {
            callback.onComplete(match { it.exceptionOrNull() === persistenceFailure })
        }
    }

    @Test
    fun onDeepLink_Does_Not_Let_A_Persistence_Failure_Escape() {
        every { dataStoreEditor.commit() } throws PersistenceException("", Exception())

        // an escape here reaches the scheduler task, where nothing catches it
        activities.onNext(
            ActivityManager.ActivityStatus(
                activity = mockActivity(Intent.ACTION_VIEW),
                type = ActivityManager.ActivityLifecycleType.Created
            )
        )

        verify { dataStoreEditor.commit() }
    }

    @Test
    fun handle_Trace_Reports_Failure_When_Join_Fails() {
        val traceId = "test_trace_id"
        val joinFailure = TealiumException("join failed")
        every { traceModule.join(traceId) } throws joinFailure
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        // reported through the callback, never propagated
        deepLinkModule.handle(traceUri(traceId), callback = callback)

        verify(exactly = 1) {
            callback.onComplete(match { it.exceptionOrNull() === joinFailure })
        }
        verify(exactly = 0) {
            dataStoreEditor.commit()
            tracker.track(any(), any(), any())
        }
    }

    @Test
    fun handle_Trace_Reports_Failure_When_ForceEndOfVisit_Fails() {
        // the ordinary first-use case: forceEndOfVisit runs before join, and throws when
        // no trace is already persisted
        val forceEndOfVisitFailure = TealiumException("Not in an active Trace")
        every { traceModule.forceEndOfVisit(any()) } throws forceEndOfVisitFailure
        val traceId = "test_trace_id"
        val link = traceUri(traceId).buildUpon()
            .appendQueryParameter(DeepLinkModule.FORCE_END_OF_VISIT, "true")
            .build()
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        // reported through the callback, never propagated
        deepLinkModule.handle(link, callback = callback)

        verify(exactly = 1) {
            callback.onComplete(match { it.exceptionOrNull() === forceEndOfVisitFailure })
        }
        verify(exactly = 0) {
            traceModule.join(any())
        }
    }

    @Test
    fun onDeepLink_Does_Not_Let_A_Trace_Failure_Escape() {
        val traceId = "test_trace_id"
        every { traceModule.join(traceId) } throws TealiumException("join failed")

        // an escape here reaches the scheduler task, where nothing catches it
        activities.onNext(
            ActivityManager.ActivityStatus(
                activity = mockActivity(Intent.ACTION_VIEW, uri = traceUri(traceId)),
                type = ActivityManager.ActivityLifecycleType.Created
            )
        )

        verify { traceModule.join(traceId) }
    }

    @Test
    fun onDeepLink_Persists_DeepLink_Data_When_Activity_Created_With_ViewIntent() {
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
    fun onDeepLink_Adds_Referrer_When_Referrer_Available() {
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
    fun onDeepLink_Does_Not_Add_Referrer_When_No_Referrer_Info() {
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
    fun onDeepLink_Is_Not_Called_When_Activity_Not_Created() {
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
    fun onDeepLink_Is_Not_Called_When_Intent_Not_ViewAction() {
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
    fun updateConfiguration_Disposes_And_Recreates_Subscription_When_Automatic_Tracking_Toggles() {
        val activitiesSource = Observables.publishSubject<ActivityManager.ActivityStatus>()
        val counting = CountingObservable(activitiesSource)

        deepLinkModule = createDeepLinkModule(
            activities = counting,
            configuration = DeepLinkModuleConfiguration(automaticDeepLinkTracking = true)
        )
        assertEquals(1, counting.activeSubscriptions)

        val disabledConfig = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(false)
            .buildConfiguration()
        deepLinkModule.updateConfiguration(disabledConfig)

        assertEquals(0, counting.activeSubscriptions)

        val enabledConfig = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(true)
            .buildConfiguration()
        deepLinkModule.updateConfiguration(enabledConfig)

        // a new subscription, not the disposed one revived
        assertEquals(1, counting.activeSubscriptions)
        assertEquals(2, counting.subscribeCalls)

        // already subscribed; the guard must not create a third
        deepLinkModule.updateConfiguration(enabledConfig)

        assertEquals(2, counting.subscribeCalls)
    }

    @Test
    fun onShutdown_Fails_Pending_Trace_Callback_And_Does_Not_Join_Trace() {
        val freshModules = Observables.stateSubject(listOf<Module>()) // TraceModule not yet ready
        val freshModuleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS, freshModules)
        val freshDeepLinkModule = createDeepLinkModule(moduleManager = freshModuleManager)
        val traceId = "test_trace_id"
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        freshDeepLinkModule.handle(traceUri(traceId), callback = callback)
        freshDeepLinkModule.onShutdown()

        // a later settings pass must not resolve the abandoned lookup
        freshModules.onNext(listOf(traceModule))

        verify(exactly = 1) {
            callback.onComplete(any())
        }
        verify {
            callback.onComplete(shutdownFailure())
        }
        verify(exactly = 0) {
            traceModule.join(any())
        }
    }

    @Test
    fun onShutdown_Does_Not_Complete_Callback_Again_When_Trace_Already_Resolved() {
        val traceId = "test_trace_id"
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        deepLinkModule.handle(traceUri(traceId), callback = callback)
        deepLinkModule.onShutdown()

        verify(exactly = 1) {
            callback.onComplete(any())
        }
        verify {
            callback.onComplete(match { it.isSuccess })
        }
        verify {
            traceModule.join(traceId)
        }
    }

    @Test
    fun handle_Fails_Callback_Once_When_Module_Shut_Down_And_Trace_Already_Ready() {
        val traceId = "test_trace_id"
        val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

        deepLinkModule.onShutdown()
        deepLinkModule.handle(traceUri(traceId), callback = callback)

        verify(exactly = 1) {
            callback.onComplete(any())
        }
        verify {
            callback.onComplete(shutdownFailure())
        }
        verify(exactly = 0) {
            traceModule.join(any())
            tracker.track(any(), any(), any())
        }
    }

    @Test
    fun onShutdown_Disposes_Activity_Subscription() {
        val activitiesSource = Observables.publishSubject<ActivityManager.ActivityStatus>()
        val counting = CountingObservable(activitiesSource)

        deepLinkModule = createDeepLinkModule(
            activities = counting,
            configuration = DeepLinkModuleConfiguration(automaticDeepLinkTracking = true)
        )

        deepLinkModule.onShutdown()

        assertEquals(1, counting.subscribeCalls)
        assertEquals(0, counting.activeSubscriptions)
    }

    @Test
    fun factory_ModuleType_Returns_Expected_Module_Id() {
        val factory = DeepLinkModule.Factory(DataObject.EMPTY_OBJECT)

        assertEquals(Modules.Types.DEEP_LINK, factory.moduleType)
    }

    @Test
    fun factory_GetEnforcedSettings_Returns_Provided_Settings() {
        val enforcedSettings = DataObject.create {
            put(DeepLinkModuleConfiguration.KEY_AUTOMATIC_DEEPLINK_TRACKING, false)
            put(DeepLinkModuleConfiguration.KEY_SEND_DEEPLINK_EVENT, true)
        }

        val factory = DeepLinkModule.Factory(enforcedSettings)

        assertEquals(listOf(enforcedSettings), factory.getEnforcedSettings())
    }

    @Test
    fun factory_Constructor_With_DeepLinkSettingsBuilder_Creates_Factory_With_Builder_Settings() {
        val settingsBuilder = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(false)
        val expected = settingsBuilder.build()

        val factory = DeepLinkModule.Factory(settingsBuilder)

        assertEquals(listOf(expected), factory.getEnforcedSettings())
    }

    @Test
    fun factory_Create_Returns_DeepLinkHandlerModule_With_Configuration() {
        val factory = DeepLinkModule.Factory(DataObject.EMPTY_OBJECT)
        val tealiumContext = mockk<TealiumContext>()
        val storageProvider = mockk<ModuleStoreProvider>()

        every { tealiumContext.storageProvider } returns storageProvider
        every { tealiumContext.tracker } returns tracker
        every { tealiumContext.moduleManager } returns moduleManager
        every { tealiumContext.activityManager.activities } returns activities
        every { tealiumContext.logger } returns SystemLogger
        every { storageProvider.getModuleStore(factory.moduleType) } returns dataStore

        val configuration = DeepLinkSettingsBuilder()
            .setAutomaticDeepLinkTrackingEnabled(true)
            .buildConfiguration()

        val module = factory.create(Modules.Types.DEEP_LINK, tealiumContext, configuration)

        assertNotNull(module)
        assertTrue(module is DeepLinkModule)
        assertEquals(Modules.Types.DEEP_LINK, module?.id)
    }

    private fun createDeepLinkModule(
        activities: Observable<ActivityManager.ActivityStatus> = this.activities,
        moduleManager: ModuleManager = this.moduleManager,
        configuration: DeepLinkModuleConfiguration = this.configuration
    ): DeepLinkModule = DeepLinkModule(
        dataStore,
        tracker,
        moduleManager,
        activities,
        configuration,
        SystemLogger
    )

    private fun traceUri(traceId: String): Uri =
        uri.buildUpon()
            .appendQueryParameter(DeepLinkModule.TRACE_ID_QUERY_PARAM, traceId)
            .build()

    private fun MockKMatcherScope.shutdownFailure(): TealiumResult<Unit> =
        match {
            it.isFailure && (it.exceptionOrNull() as? ModuleShutdownException)
                ?.moduleId == Modules.Types.DEEP_LINK
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            every {
                intent.getParcelableExtra(Intent.EXTRA_REFERRER, Uri::class.java)
            } returns referrer
        }
        every { intent.getParcelableExtra<Uri>(Intent.EXTRA_REFERRER) } returns referrer
        every { intent.getStringExtra(Intent.EXTRA_REFERRER_NAME) } returns referrer?.toString()

        return activity
    }

    private class CountingObservable<T>(private val source: Observable<T>) : Observable<T> {
        private val disposables = mutableListOf<Disposable>()

        val subscribeCalls get() = disposables.size
        val activeSubscriptions get() = disposables.count { !it.isDisposed }

        override fun subscribe(observer: Observer<T>): Disposable =
            source.subscribe(observer).also(disposables::add)
    }
}

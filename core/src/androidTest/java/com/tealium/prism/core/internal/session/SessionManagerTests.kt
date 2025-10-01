package com.tealium.prism.core.internal.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.hours
import com.tealium.prism.core.api.misc.TimeFrameUtils.inMilliseconds
import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.session.Session
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.database.getTimestampMilliseconds
import com.tealium.prism.core.internal.persistence.repositories.ModulesRepository
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.prism.core.internal.pubsub.Subscription
import com.tealium.tests.common.ManualScheduler
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.getDefaultConfig
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionManagerTests {

    private val context: Application = ApplicationProvider.getApplicationContext() as Application
    private val config = getDefaultConfig(context)
    private val logger = SystemLogger

    private val testSessionTimeout = Observables.stateSubject(5.minutes)
    private val timingProvider = ::getTimestampMilliseconds
    private val testInitTime = timingProvider.invoke()

    private lateinit var scheduler: Scheduler
    private lateinit var dataStore: DataStore
    private lateinit var modulesRepository: ModulesRepository

    private lateinit var observer: Observer<Session>
    private lateinit var sessionManager: SessionManagerImpl

    @Before
    fun setUp() {
        val dbProvider = InMemoryDatabaseProvider(config)
        modulesRepository = SQLModulesRepository(dbProvider)
        val moduleStoreProvider = ModuleStoreProviderImpl(dbProvider, modulesRepository)
        dataStore = moduleStoreProvider.getSharedDataStore()
        observer = mockk<Observer<Session>>(relaxed = true)
        scheduler = mockScheduler()
    }

    @Test
    fun init_Does_Not_Emit_A_Session_When_No_Existing_Session_And_No_Events() {
        sessionManager = createSessionManager()
        sessionManager.session.subscribe(observer)

        verify { observer wasNot Called }
    }

    @Test
    fun init_Clears_Session_Data_When_No_Existing_Session() {
        dataStore.edit()
            .put("test", "value", Expiry.SESSION)
            .commit()

        sessionManager = createSessionManager()

        assertNull(dataStore.get("test"))
    }

    @Test
    fun init_Resumes_Existing_Session_When_Session_Not_Expired() {
        listOf(testInitTime, testInitTime - 4.minutes.inMilliseconds()).forEach {
            observer = mockk<Observer<Session>>(relaxed = true)
            storeSession(it)

            sessionManager = createSessionManager()
            sessionManager.session.subscribe(observer)

            verify { observer.onNext(match { it.status == Session.Status.Resumed }) }
        }
    }

    @Test
    fun init_Schedules_Expiration_From_Last_Event_Time_When_Session_Resumed() {
        val timingProvider = mockTimingProvider(
            testInitTime,                               // initial check
            testInitTime + 2.minutes.inMilliseconds()   // check for now
        )
        storeSession(testInitTime.div(1000), testInitTime)

        sessionManager =
            createSessionManager(scheduler = scheduler, timingProvider = timingProvider)

        verify {
            scheduler.schedule(match { delay ->
                delay.inMilliseconds() ==
                        testSessionTimeout.value.inMilliseconds() - 2.minutes.inMilliseconds()
            }, any())
        }
    }

    @Test
    fun init_Does_Not_Clear_Session_Data_When_Session_Resumed() {
        storeSession(testInitTime)
        dataStore.edit()
            .put("test", "value", Expiry.SESSION)
            .commit()

        sessionManager = createSessionManager()

        assertEquals("value", dataStore.getString("test"))
    }

    @Test
    fun init_Ends_Session_When_Existing_Session_Expired() {
        val sixMinutesAgo = testInitTime - 6.minutes.inMilliseconds()
        storeSession(sixMinutesAgo)

        sessionManager = createSessionManager()
        sessionManager.session.subscribe(observer)

        verify {
            observer.onNext(match { it.status == Session.Status.Ended })
        }
    }

    @Test
    fun init_Clears_Session_Data_When_Session_Ended() {
        val sixMinutesAgo = testInitTime - 6.minutes.inMilliseconds()
        storeSession(sixMinutesAgo)
        dataStore.edit()
            .put("test", "value", Expiry.SESSION)
            .commit()

        sessionManager = createSessionManager()

        assertNull(dataStore.get("key"))
    }

    @Test
    fun registerDispatch_Starts_New_Session_When_No_Session() {
        val dispatch = Dispatch.create("event", DataObject.EMPTY_OBJECT, testInitTime)

        sessionManager = createSessionManager()
        sessionManager.session.subscribe(observer)

        sessionManager.registerDispatch(dispatch)

        verify {
            observer.onNext(match {
                it.status == Session.Status.Started
                        && it.lastEventTimeMilliseconds == testInitTime
            })
        }
    }

    @Test
    fun registerDispatch_Starts_New_Session_When_Session_Ended() {
        storeSession(testInitTime)

        val manualScheduler = ManualScheduler()
        sessionManager = createSessionManager(scheduler = manualScheduler)
        sessionManager.session.subscribe(observer)
        manualScheduler.runNext() // end session

        val sixMinutesLater = testInitTime + 6.minutes.inMilliseconds()
        val dispatch = Dispatch.create("event", DataObject.EMPTY_OBJECT, sixMinutesLater)
        sessionManager.registerDispatch(dispatch)

        verify {
            observer.onNext(match {
                it.status == Session.Status.Ended
                        && it.lastEventTimeMilliseconds == testInitTime
            })
            observer.onNext(match {
                it.status == Session.Status.Started
                        && it.lastEventTimeMilliseconds == sixMinutesLater
            })
        }
    }

    @Test
    fun registerDispatch_Extends_Session_When_Session_Not_Expired() {
        storeSession(testInitTime)
        val dispatchTimestamp = testInitTime + 1.minutes.inMilliseconds()
        val dispatch = Dispatch.create("event", DataObject.EMPTY_OBJECT, dispatchTimestamp)

        sessionManager = createSessionManager()
        sessionManager.registerDispatch(dispatch)

        assertEquals(testInitTime, dataStore.sessionId)
        assertEquals(dispatchTimestamp, dataStore.lastEventTime)
    }

    @Test
    fun registerDispatch_Schedules_Expiry_From_Dispatch_Timestamp_When_New_Session_Started() {
        val timingProvider = mockTimingProvider(testInitTime) // always return testInitTime

        sessionManager = createSessionManager(timingProvider = timingProvider, scheduler = scheduler)

        val fiveSecondsAgo = testInitTime - 5.seconds.inMilliseconds()
        val dispatch = Dispatch.create("event", DataObject.EMPTY_OBJECT, fiveSecondsAgo)
        sessionManager.registerDispatch(dispatch)

        verify {
            scheduler.schedule(match { delay ->
                delay.inMilliseconds() ==
                        testSessionTimeout.value.inMilliseconds() - 5.seconds.inMilliseconds()
            }, any())
        }
    }

    @Test
    fun registerDispatch_Schedules_Expiry_From_Dispatch_Timestamp_When_Extending_Session() {
        storeSession(testInitTime)
        val extendedEventTime = testInitTime + 10.seconds.inMilliseconds()
        val timingProvider = mockTimingProvider(
            testInitTime,       // init time check
            testInitTime,       // resumed expiration
            extendedEventTime   // extended expiration
        )

        sessionManager = createSessionManager(timingProvider = timingProvider, scheduler = scheduler)

        val fiveSecondsLater = testInitTime + 5.seconds.inMilliseconds()
        val dispatch = Dispatch.create("event", DataObject.EMPTY_OBJECT, fiveSecondsLater)
        sessionManager.registerDispatch(dispatch)

        verify {
            scheduler.schedule(match { delay ->
                delay.inMilliseconds() ==
                        testSessionTimeout.value.inMilliseconds() - (extendedEventTime - dispatch.timestamp)
            }, any())
        }
    }

    @Test
    fun registerDispatch_Does_Not_Change_Session_Id() {
        storeSession(testInitTime, eventCount = 1)
        val dispatch = Dispatch.create(
            "event",
            DataObject.EMPTY_OBJECT,
            testInitTime + 1.minutes.inMilliseconds()
        )

        sessionManager = createSessionManager()
        sessionManager.session.subscribe(observer)

        sessionManager.registerDispatch(dispatch)
        sessionManager.registerDispatch(dispatch)

        verify(exactly = 3) {
            observer.onNext(match { it.sessionId == testInitTime })
        }
    }

    @Test
    fun registerDispatch_Increments_Session_Event_Count() {
        storeSession(testInitTime, eventCount = 1)
        val dispatch = Dispatch.create(
            "event",
            DataObject.EMPTY_OBJECT,
            testInitTime + 1.minutes.inMilliseconds()
        )

        sessionManager = createSessionManager()
        sessionManager.session.subscribe(observer)

        sessionManager.registerDispatch(dispatch)
        sessionManager.registerDispatch(dispatch)
        sessionManager.registerDispatch(dispatch)

        verify {
            observer.onNext(match { it.eventCount == 1 })
            observer.onNext(match { it.eventCount == 2 })
            observer.onNext(match { it.eventCount == 3 })
            observer.onNext(match { it.eventCount == 4 })
        }
    }

    @Test
    fun registerDispatch_Updates_Last_Event_Time() {
        storeSession(testInitTime)
        val dispatch1 = Dispatch.create(
            "event",
            DataObject.EMPTY_OBJECT,
            testInitTime + 1.minutes.inMilliseconds()
        )
        val dispatch2 = Dispatch.create(
            "event",
            DataObject.EMPTY_OBJECT,
            testInitTime + 2.minutes.inMilliseconds()
        )

        sessionManager = createSessionManager()
        sessionManager.session.subscribe(observer)

        sessionManager.registerDispatch(dispatch1)
        sessionManager.registerDispatch(dispatch2)

        verify {
            observer.onNext(match { it.lastEventTimeMilliseconds == testInitTime })
            observer.onNext(match { it.lastEventTimeMilliseconds == dispatch1.timestamp })
            observer.onNext(match { it.lastEventTimeMilliseconds == dispatch2.timestamp })
        }
    }

    @Test
    fun registerDispatch_Does_Not_Emit_Started_When_Session_Resumed_And_Not_Expired() {
        storeSession(testInitTime)
        val dispatchTimestamp = testInitTime + 1.minutes.inMilliseconds()
        val dispatch1 = Dispatch.create("event", DataObject.EMPTY_OBJECT, dispatchTimestamp)
        val dispatch2 = Dispatch.create(
            "event",
            DataObject.EMPTY_OBJECT,
            dispatchTimestamp + 1.minutes.inMilliseconds()
        )
        val dispatch3 = Dispatch.create(
            "event",
            DataObject.EMPTY_OBJECT,
            dispatchTimestamp + 2.minutes.inMilliseconds()
        )

        sessionManager = createSessionManager()
        sessionManager.session.subscribe(observer)

        sessionManager.registerDispatch(dispatch1)
        sessionManager.registerDispatch(dispatch2)
        sessionManager.registerDispatch(dispatch3)

        verify(inverse = true) {
            observer.onNext(match { it.status == Session.Status.Started })
        }
    }

    @Test
    fun registerDispatch_Adds_SessionTimeout_To_Dispatch() {
        sessionManager = createSessionManager()

        listOf(5.minutes, 10.minutes, 30.minutes).forEach { sessionTimeout ->
            testSessionTimeout.onNext(sessionTimeout)
            val dispatch = Dispatch.create("event", DataObject.EMPTY_OBJECT, testInitTime)

            sessionManager.registerDispatch(dispatch)

            assertEquals(
                sessionTimeout.inMilliseconds(),
                dispatch.payload().getLong(Dispatch.Keys.TEALIUM_SESSION_TIMEOUT)
            )
        }
    }

    @Test
    fun registerDispatch_Adds_Session_Id_To_Dispatch_But_Not_Is_New_Session_When_Not_First_Event() {
        storeSession(testInitTime, eventCount = 1)
        val dispatchTimestamp = testInitTime + 1.minutes.inMilliseconds()
        val dispatch = Dispatch.create("event", DataObject.EMPTY_OBJECT, dispatchTimestamp)

        sessionManager = createSessionManager()
        sessionManager.registerDispatch(dispatch)

        assertEquals(testInitTime, dispatch.payload().getLong(Dispatch.Keys.TEALIUM_SESSION_ID))
        assertNull(dispatch.payload().get(Dispatch.Keys.IS_NEW_SESSION))
    }

    @Test
    fun registerDispatch_Adds_Is_New_Session_When_No_Previous_Session() {
        val dispatch = Dispatch.create("event", DataObject.EMPTY_OBJECT, testInitTime)

        sessionManager = createSessionManager()
        sessionManager.registerDispatch(dispatch)

        assertEquals(true, dispatch.payload().getBoolean(Dispatch.Keys.IS_NEW_SESSION))
    }

    @Test
    fun session_Observable_Emits_Ended_Event_When_Sessions_Expire() {
        val observer = mockk<Observer<Session>>(relaxed = true)
        storeSession(testInitTime)
        val manualScheduler = ManualScheduler()

        sessionManager =
            createSessionManager(scheduler = manualScheduler)
        sessionManager.session.subscribe(observer)

        // execute expiration task
        manualScheduler.runNext()

        verifyOrder {
            observer.onNext(match { it.status == Session.Status.Resumed })
            observer.onNext(match { it.status == Session.Status.Ended })
        }
        verify(inverse = true) {
            observer.onNext(match { it.status == Session.Status.Started })
        }
    }

    @Test
    fun session_Module_Data_Is_Deleted_When_Session_Ends() {
        dataStore.edit()
            .put("test", "value", Expiry.SESSION)
            .commit()
        storeSession(testInitTime)
        val manualScheduler = ManualScheduler()

        sessionManager =
            createSessionManager(scheduler = manualScheduler)
        dataStore.edit()
            .put("after-init", "value", Expiry.SESSION)
            .commit()

        // execute expiration task
        manualScheduler.runNext()

        assertNull(dataStore.get("key"))
        assertNull(dataStore.get("after-init"))
    }

    @Test
    fun shutdown_Cancels_Expiration_Task() {
        storeSession(testInitTime)
        val manualScheduler = ManualScheduler()

        sessionManager =
            createSessionManager(scheduler = manualScheduler)
        sessionManager.session.subscribe(observer)

        sessionManager.shutdown()
        // execute expiration task
        manualScheduler.runNext()

        verify(inverse = true) {
            observer.onNext(match { it.status == Session.Status.Ended })
        }
    }

    @Test
    fun sessionInfo_isExpired_Returns_True_When_Session_Expired() {
        val currentTime = 1L
        val session = SessionInfo(currentTime, currentTime)
        val sessionTimeout = 5.minutes

        assertTrue(session.isExpired(currentTime + 6.minutes.inMilliseconds(), sessionTimeout))
        assertTrue(session.isExpired(currentTime + 10.minutes.inMilliseconds(), sessionTimeout))
        assertTrue(session.isExpired(currentTime + 300_001L, sessionTimeout))
    }

    @Test
    fun sessionInfo_isExpired_Returns_False_When_Session_Not_Expired() {
        val currentTime = 1L
        val session = SessionInfo(currentTime, currentTime)
        val sessionTimeout = 5.minutes

        assertFalse(session.isExpired(currentTime + 0.minutes.inMilliseconds(), sessionTimeout))
        assertFalse(session.isExpired(currentTime + 1.minutes.inMilliseconds(), sessionTimeout))
        assertFalse(session.isExpired(currentTime + 4.minutes.inMilliseconds(), sessionTimeout))
        assertFalse(session.isExpired(currentTime + 299_999L, sessionTimeout))
    }

    @Test
    fun sessionInfo_isExpired_Returns_False_When_Current_Time_Equals_Expiry_Time() {
        val currentTime = 1L
        val session = SessionInfo(currentTime, currentTime)
        val sessionTimeout = 5.minutes

        assertFalse(session.isExpired(currentTime + 5.minutes.inMilliseconds(), sessionTimeout))
        assertFalse(session.isExpired(currentTime + 300_000L, sessionTimeout))
    }

    @Test
    fun coerceSessionTimeout_Returns_Minimum_Length_When_Less_Than_Or_Equal() {
        val min = SessionManagerImpl.MINIMUM_SESSION_TIMEOUT
        assertEquals(min, SessionManagerImpl.coerceSessionTimeout(1.seconds))
        assertEquals(min, SessionManagerImpl.coerceSessionTimeout((-1).seconds))
        assertEquals(min, SessionManagerImpl.coerceSessionTimeout(min))
    }

    @Test
    fun coerceSessionTimeout_Returns_Maximum_Length_When_Greater_Than_Or_Equal() {
        val max = SessionManagerImpl.MAXIMUM_SESSION_TIMEOUT
        assertEquals(max, SessionManagerImpl.coerceSessionTimeout(1.hours))
        assertEquals(max, SessionManagerImpl.coerceSessionTimeout(100.minutes))
        assertEquals(max, SessionManagerImpl.coerceSessionTimeout(1_000_000.seconds))
        assertEquals(max, SessionManagerImpl.coerceSessionTimeout(max))
    }

    private fun createSessionManager(
        sessionTimeout: ObservableState<TimeFrame> = this.testSessionTimeout,
        dataStore: DataStore = this.dataStore,
        timingProvider: () -> Long = this.timingProvider,
        scheduler: Scheduler = this.scheduler,
        modulesRepository: ModulesRepository = this.modulesRepository
    ): SessionManagerImpl =
        SessionManagerImpl(
            sessionTimeout,
            dataStore,
            scheduler,
            modulesRepository,
            logger,
            timingProvider,
        )

    private fun mockScheduler() : Scheduler {
        val scheduler = mockk<Scheduler>()
        every { scheduler.execute(any()) } returns Unit
        every { scheduler.schedule(any()) } returns Subscription()
        every { scheduler.schedule(any(), any()) } returns Subscription()
        return scheduler
    }

    private fun mockTimingProvider(vararg timeInMs: Long) : () -> Long {
        if (timeInMs.isEmpty()) throw IllegalArgumentException("List of times cannot be empty.")

        val mockTimingProvider = mockk<() -> Long>()
        every { mockTimingProvider.invoke() } returnsMany timeInMs.asList()

        return mockTimingProvider
    }

    private fun storeSession(
        sessionId: Long,
        lastEventTime: Long = sessionId,
        eventCount: Int = 1
    ) {
        val session = SessionInfo(sessionId, lastEventTime, eventCount)
        dataStore.edit()
            .put(SessionManagerImpl.KEY_SESSION_INFO, session, Expiry.FOREVER)
            .commit()
    }

    private val DataStore.sessionId
        get() = sessionInfo?.sessionId

    private val DataStore.lastEventTime
        get() = sessionInfo?.lastEventTimeMilliseconds

    private val DataStore.sessionInfo
        get() = get(SessionManagerImpl.KEY_SESSION_INFO, SessionInfo.Converter)

    private fun ManualScheduler.runNext() {
        queue.poll()?.runnable?.run()
    }
}

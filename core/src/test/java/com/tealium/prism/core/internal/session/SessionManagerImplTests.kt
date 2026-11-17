package com.tealium.prism.core.internal.session

import android.database.sqlite.SQLiteFullException
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.TimeFrameUtils.inMilliseconds
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.session.Session
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.persistence.database.getTimestampMilliseconds
import com.tealium.prism.core.internal.persistence.repositories.ModulesRepository
import com.tealium.tests.common.ManualScheduler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionManagerImplTests {

    private val dataStore: DataStore = mockk(relaxed = true)
    private val modulesRepository: ModulesRepository = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)
    private val scheduler = ManualScheduler()

    private val sessions = mutableListOf<Session>()

    @Before
    fun setUp() {
        every {
            dataStore.get(any<String>(), any<DataItemConverter<SessionInfo>>())
        } returns null
        every { logger.shouldLog(LogLevel.ERROR) } returns true
    }

    @Test
    fun session_Emits_Ended_And_Logs_Error_When_Expiration_Cleanup_Fails() {
        val sessionManager = createSessionManager()
        sessionManager.registerDispatch(Dispatch.create("event"))
        failCleanup()

        scheduler.runAll() // runs the expiration task

        assertEquals(
            listOf(Session.Status.Started, Session.Status.Ended),
            sessions.map { it.status }
        )
        verify(exactly = 1) { logger.error(any(), any<String>()) }
    }

    @Test
    fun init_Emits_Ended_And_Logs_Error_When_Existing_Session_Expired_And_Cleanup_Fails() {
        val expiredLastEventTime = getTimestampMilliseconds() -
                SessionManagerImpl.DEFAULT_SESSION_TIMEOUT.inMilliseconds() - 1
        every {
            dataStore.get(any<String>(), any<DataItemConverter<SessionInfo>>())
        } returns SessionInfo(sessionId = 1L, lastEventTimeMilliseconds = expiredLastEventTime)
        failCleanup()

        createSessionManager()

        assertEquals(listOf(Session.Status.Ended), sessions.map { it.status })
        verify(exactly = 1) { logger.error(any(), any<String>()) }
    }

    @Test
    fun init_Logs_Error_And_Completes_When_No_Existing_Session_And_Cleanup_Fails() {
        // setUp stubs no stored session
        failCleanup()

        createSessionManager()

        assertEquals(emptyList<Session>(), sessions)
        verify(exactly = 1) { logger.error(any(), any<String>()) }
    }

    private fun failCleanup() {
        every {
            modulesRepository.deleteExpired(ModulesRepository.ExpirationType.Session, any())
        } throws SQLiteFullException("database or disk is full")
    }

    private fun createSessionManager(): SessionManagerImpl =
        SessionManagerImpl(
            Observables.stateSubject(SessionManagerImpl.DEFAULT_SESSION_TIMEOUT),
            dataStore,
            scheduler,
            modulesRepository,
            logger,
            ::getTimestampMilliseconds
        ).also { it.session.subscribe { session -> sessions.add(session) } }
}

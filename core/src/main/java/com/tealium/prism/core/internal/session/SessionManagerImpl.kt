package com.tealium.prism.core.internal.session

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfDebugEnabled
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.inMilliseconds
import com.tealium.prism.core.api.misc.TimeFrameUtils.inSeconds
import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.api.session.Session
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.persistence.database.getTimestampMilliseconds
import com.tealium.prism.core.internal.persistence.repositories.ModulesRepository
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import java.util.concurrent.TimeUnit

/**
 * Handles the starting and stopping of sessions, as well as expiration of session-scoped data.
 *
 * Sessions are tied explicitly to events. They are started and extended only by events registered
 * via [registerDispatch].
 *
 * They are expired eagerly when no new events have been received, where the previous event is older
 * than the given [sessionTimeout]. Or during initialization, if the previous session has expired.
 *
 * On subsequent launches, if a session is still active, it will be resumed and extended to the current sessionTimeout.
 */
class SessionManagerImpl(
    sessionTimeout: ObservableState<TimeFrame> = Observables.stateSubject(DEFAULT_SESSION_TIMEOUT),
    private val dataStore: DataStore,
    private val scheduler: Scheduler,
    private val modulesRepository: ModulesRepository,
    private val logger: Logger,
    private val timingProvider: () -> Long = ::getTimestampMilliseconds,
) : SessionManager {

    private val disposables = DisposableContainer()
    private var expirationTask: Disposable? = null
    private val sessionTimeout: ObservableState<TimeFrame> =
        sessionTimeout.mapState(::coerceSessionTimeout)

    private val _session: ReplaySubject<Session> = Observables.replaySubject(1)
    override val session: Observable<Session> = _session.asObservable()

    init {
        val existingSession = loadExistingSession()
        if (existingSession == null) {
            // It's possible that a session ended, and session-scoped data was subsequently added
            // without a current session. In that case, we delete the session-scoped data on init.
            deleteModuleSessionData()
        } else {
            val status = if (!existingSession.isExpired(timingProvider.invoke(), sessionTimeout.value)) {
                logger.logIfDebugEnabled(LogCategory.SESSION_MANAGER) {
                    "Found existing session with id ${existingSession.sessionId}; resuming."
                }
                Session.Status.Resumed
            } else {
                Session.Status.Ended
            }
            _session.onNext(existingSession.asSession(status))
        }

        // subscribe persistence and expiration listener
        _session.subscribe { updatedSession ->
            if (updatedSession.status == Session.Status.Ended) {
                deleteModuleSessionData()
                return@subscribe
            }

            val sessionInfo =
                SessionInfo(
                    updatedSession.sessionId,
                    updatedSession.lastEventTimeMilliseconds,
                    updatedSession.eventCount
                )
            storeSession(sessionInfo)
            scheduleExpireTask(sessionInfo)
        }.addTo(disposables)
    }

    /**
     * Clears all session-scoped data stored by the modules
     */
    private fun deleteModuleSessionData() {
        modulesRepository.deleteExpired(ModulesRepository.ExpirationType.Session)
    }

    /**
     * Shuts down the Session manager by disposing of the expiration task.
     */
    fun shutdown() {
        disposables.dispose()
    }

    /**
     * Reads the existing session data from disk and returns it as a [SessionInfo] object; else null
     */
    private fun loadExistingSession(): SessionInfo? {
        return try {
            return dataStore.get(KEY_SESSION_INFO, SessionInfo.Converter)
        } catch (ex: PersistenceException) {
            logger.logIfWarnEnabled(LogCategory.SESSION_MANAGER) {
                "Error reading from session DataStore: ${ex.message}"
            }
            null
        }
    }

    /**
     * Saves the session information to disk.
     */
    private fun storeSession(session: SessionInfo) {
        try {
            dataStore.edit()
                .put(KEY_SESSION_INFO, session, Expiry.SESSION)
                .commit()
        } catch (ex: PersistenceException) {
            logger.logIfWarnEnabled(LogCategory.SESSION_MANAGER) {
                "Error writing updated session data: ${ex.message}"
            }
        }
    }

    /**
     * Cancels any previous expiration task, and schedules a new one.
     */
    private fun scheduleExpireTask(
        session: SessionInfo
    ) {
        val sessionTimeout = sessionTimeout.value
        val nowMs = timingProvider.invoke()
        val expiryMs = session.lastEventTimeMilliseconds + sessionTimeout.inMilliseconds()
        val delay = expiryMs - nowMs

        expirationTask?.let { existingTask ->
            disposables.remove(existingTask)
            existingTask.dispose()
        }
        expirationTask = scheduler.schedule(TimeFrame(delay, TimeUnit.MILLISECONDS)) {
            logger.logIfDebugEnabled(LogCategory.SESSION_MANAGER) {
                "Session expired (${session})"
            }
            _session.onNext(session.asSession(Session.Status.Ended))
        }.addTo(disposables)
    }

    /**
     * If the existing session status is [Session.Status.Ended] then a new session is generated.
     *
     * Otherwise the session event count is incremented and event timestamps are updated.
     */
    private fun createOrExtendSession(session: Session?, newEventTimestamp: Long): Session {
        if (session == null || session.status == Session.Status.Ended) {
            val newSession = Session(
                status = Session.Status.Started,
                sessionId = newEventTimestamp.div(1000),
                lastEventTimeMilliseconds = newEventTimestamp,
                eventCount = 1
            )
            logger.logIfDebugEnabled(LogCategory.SESSION_MANAGER) {
                "Starting new session with id ${newSession.sessionId}"
            }
            return newSession
        }

        return session.incrementAndExtend(newEventTimestamp)
    }

    override fun registerDispatch(dispatch: Dispatch) {
        val session = createOrExtendSession(_session.last(), dispatch.timestamp)

        val sessionData = DataObject.create {
            if (session.eventCount == 1) {
                put(Dispatch.Keys.IS_NEW_SESSION, true)
            }

            put(Dispatch.Keys.TEALIUM_SESSION_ID, session.sessionId)
            // note. at the time of writing _dc_ttl_ doesn't work for cases where `platform` is set
            put(Dispatch.Keys.TEALIUM_SESSION_TIMEOUT, sessionTimeout.value.inMilliseconds())
        }

        dispatch.addAll(sessionData)

        _session.onNext(session)
    }

    companion object {
        const val KEY_SESSION_INFO = "session_info"

        val DEFAULT_SESSION_TIMEOUT = 5.minutes

        // Min and Max session times according to CDH.
        val MINIMUM_SESSION_TIMEOUT = 5.seconds
        val MAXIMUM_SESSION_TIMEOUT = 30.minutes

        /**
         * Coerces the given [sessionTimeout] to be between the [MINIMUM_SESSION_TIMEOUT]
         * and [MAXIMUM_SESSION_TIMEOUT]
         */
        fun coerceSessionTimeout(sessionTimeout: TimeFrame): TimeFrame {
            val seconds = sessionTimeout.inSeconds()
            return if (seconds < MINIMUM_SESSION_TIMEOUT.inSeconds()) {
                MINIMUM_SESSION_TIMEOUT
            } else if (seconds > MAXIMUM_SESSION_TIMEOUT.inSeconds()) {
                MAXIMUM_SESSION_TIMEOUT
            } else {
                sessionTimeout
            }
        }

        /**
         * Session event counts are updated and the session is extended.
         */
        @Suppress("NOTHING_TO_INLINE")
        private inline fun Session.incrementAndExtend(
            eventTimestamp: Long,
        ): Session {
            val extendedSession = this.copy(
                lastEventTimeMilliseconds = eventTimestamp,
                eventCount = this.eventCount + 1
            )

            return extendedSession
        }
    }
}
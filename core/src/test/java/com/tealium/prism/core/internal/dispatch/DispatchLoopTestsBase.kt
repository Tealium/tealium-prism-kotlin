package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.Barrier
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchType
import com.tealium.prism.core.api.tracking.TrackResult
import com.tealium.prism.core.internal.barriers.BarrierCoordinatorImpl
import com.tealium.prism.core.internal.barriers.BatchingBarrier
import com.tealium.prism.core.internal.consent.ConsentManager
import com.tealium.prism.core.internal.persistence.repositories.VolatileQueueRepository
import com.tealium.prism.core.internal.pubsub.Subscription
import com.tealium.prism.core.internal.rules.LoadRuleEngineImpl
import com.tealium.prism.core.internal.settings.CoreSettingsImpl
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.prism.core.internal.settings.consent.ConsentConfiguration
import com.tealium.prism.core.internal.settings.consent.ConsentPurpose
import com.tealium.tests.common.ManualScheduler
import com.tealium.tests.common.SystemLogger
import org.junit.Assert.assertEquals
import java.util.concurrent.TimeUnit

/**
 * Base class for the tests of the real dispatch loop: a [DispatchManagerImpl] with a single
 * [RecordingDispatcher], a real [QueueManagerImpl] and a real [BarrierCoordinatorImpl] with the
 * given barriers, all driven by a [ManualScheduler].
 *
 * Call [setUpLoop] at the start of each test, then [start] the loop.
 *
 * No application status is ever emitted: any status starts a flush, which bypasses the [BatchingBarrier].
 */
open class DispatchLoopTestsBase {
    protected val scheduler = ManualScheduler()
    private val queue: MutableMap<String, MutableSet<Dispatch>> = mutableMapOf()
    private val inFlightDispatches: StateSubject<Map<String, Set<Dispatch>>> =
        Observables.stateSubject(emptyMap())
    private var stored = 0

    protected lateinit var dispatcher: RecordingDispatcher
    protected lateinit var onDispatchers: StateSubject<List<Dispatcher>>
    protected lateinit var queueManager: CountingQueueManager
    protected lateinit var queueMetrics: QueueMetrics
    protected lateinit var barrierCoordinator: BarrierCoordinatorImpl
    protected lateinit var dispatchManager: DispatchManagerImpl
    protected var consentManager: TestConsentManager? = null
        private set

    protected val queued: Int get() = queue[DISPATCHER_ID]?.size ?: 0
    protected val inFlight: Int get() = inFlightDispatches.value[DISPATCHER_ID]?.size ?: 0
    protected val sent: Int get() = dispatcher.sentIds.size

    /**
     * Builds the loop, without starting it.
     *
     * @param consent Adds a [TestConsentManager], which has no configuration until one is published.
     * @param barriers The barriers, built once the [queueManager], [queueMetrics] and [onDispatchers] are available.
     */
    protected fun setUpLoop(
        dispatchLimit: Int,
        consent: Boolean = false,
        barriers: () -> List<Barrier> = { emptyList() }
    ) {
        dispatcher = RecordingDispatcher(DISPATCHER_ID, dispatchLimit, scheduler)
        onDispatchers = Observables.stateSubject(listOf(dispatcher))
        queueManager = CountingQueueManager(
            QueueManagerImpl(
                queueRepository = VolatileQueueRepository(queue),
                settings = Observables.stateSubject(CoreSettingsImpl(maxQueueSize = 1000)),
                processors = onDispatchers.map { dispatchers -> dispatchers.map(Dispatcher::id).toSet() },
                inFlightDispatches = inFlightDispatches,
                logger = SystemLogger
            )
        )
        queueMetrics = QueueMetricsImpl(queueManager)
        consentManager = if (consent) TestConsentManager() else null
        barrierCoordinator = BarrierCoordinatorImpl(
            barriers = Observables.just(barriers().map { it to BarrierScope.All }),
            applicationStatus = Observables.publishSubject<ActivityManager.ApplicationStatus>(),
            queueMetrics = queueMetrics
        )
        dispatchManager = DispatchManagerImpl(
            onDispatchers = onDispatchers,
            barrierCoordinator = barrierCoordinator,
            transformerCoordinator = TransformerCoordinatorImpl(
                Observables.stateSubject(emptyList()),
                Observables.stateSubject(emptyList()),
                Scheduler.SYNCHRONOUS,
                SystemLogger
            ),
            queueManager = queueManager,
            loadRuleEngine = LoadRuleEngineImpl(Observables.stateSubject(SdkSettings()), SystemLogger),
            mappingsEngine = MappingsEngine(Observables.stateSubject(emptyMap())),
            consentManager = consentManager,
            logger = SystemLogger,
            scheduler = scheduler
        )
    }

    /**
     * Adds a [BatchingBarrier] over this loop's queue, with the dispatcher's `dispatchLimit` as the
     * batch size when [batchSize] is `null`.
     */
    protected fun batchingBarrier(batchSize: Int?): Barrier =
        BatchingBarrier(queueMetrics, onDispatchers, batchSize)

    protected fun start() = dispatchManager.startDispatchLoop()

    protected fun stop() = dispatchManager.stopDispatchLoop()

    /**
     * Runs every scheduled task, follow-up dequeues and dispatcher completions included, until none is left.
     */
    protected fun settle() = scheduler.runAll()

    /**
     * Tracks [count] events one by one through the [DispatchManagerImpl]. Only enqueues them
     * without a consent manager.
     */
    protected fun track(count: Int) {
        repeat(count) {
            dispatchManager.track(Dispatch.create("event${++stored}", DispatchType.Event, DataObject.EMPTY_OBJECT))
        }
    }

    /**
     * Stores [count] consented events in the queue at once.
     */
    protected fun store(count: Int) {
        val dispatches = List(count) {
            Dispatch.create("event${++stored}", DispatchType.Event, DataObject.create {
                put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, listOf(PURPOSE_ID).asDataList())
            })
        }
        queueManager.storeDispatches(dispatches, setOf(DISPATCHER_ID))
    }

    protected fun publishConsent(configuration: ConsentConfiguration?) {
        consentManager?.configuration?.onNext(configuration)
    }

    /**
     * Runs [action] once, synchronously from within the first dequeue, right after it marks the
     * dispatches in flight.
     */
    protected fun onFirstDequeue(action: () -> Unit) {
        queueManager.inFlightCount(DISPATCHER_ID)
            .filter { it > 0 }
            .subscribeOnce { action() }
    }

    /**
     * Asserts that [sent] dispatches were sent once each, that [queued] are still in the queue, and
     * that none is in flight.
     */
    protected fun assertSettled(sent: Int, queued: Int = 0) {
        assertEquals("sent", sent, this.sent)
        assertEquals("sent only once", sent, dispatcher.sentIds.toSet().size)
        assertEquals("queued", queued, this.queued)
        assertEquals("in flight", 0, inFlight)
    }

    /**
     * Asserts that the first [count] events tracked or stored were sent in the order they were
     * added to the queue.
     */
    protected fun assertSentInOrder(count: Int) {
        assertEquals("sent order", (1..count).map { "event$it" }, dispatcher.sentNames)
    }

    companion object {
        const val DISPATCHER_ID = "recording_dispatcher"
        private const val PURPOSE_ID = "purpose1"

        val consentConfiguration = ConsentConfiguration(
            "tealium",
            emptySet(),
            mapOf(PURPOSE_ID to ConsentPurpose(PURPOSE_ID, setOf(DISPATCHER_ID)))
        )
    }
}

// region Test doubles

/**
 * A [Dispatcher] that records what it is sent and completes it asynchronously on the [scheduler].
 * It can also complete every dispatch of a batch separately, as `CollectModule` does.
 */
class RecordingDispatcher(
    override val id: String,
    override val dispatchLimit: Int,
    private val scheduler: Scheduler,
    var splitCompletions: Boolean = false
) : Dispatcher {
    override val version: String = "1.0"
    val sentIds = mutableListOf<String>()
    val sentNames = mutableListOf<String?>()
    val batchSizes = mutableListOf<Int>()
    var maxStackDepth = 0
        private set

    override fun dispatch(dispatches: List<Dispatch>, callback: Callback<List<Dispatch>>): Disposable {
        sentIds += dispatches.map(Dispatch::id)
        sentNames += dispatches.map(Dispatch::tealiumEvent)
        batchSizes += dispatches.size
        maxStackDepth = maxOf(maxStackDepth, Thread.currentThread().stackTrace.size)
        val parts = if (splitCompletions) dispatches.map(::listOf) else listOf(dispatches)
        parts.forEach { part ->
            scheduler.schedule(TimeFrame(0, TimeUnit.MILLISECONDS)) { callback.onComplete(part) }
        }
        return Subscription()
    }
}

/**
 * Counts the calls to [dequeueDispatches] of the [delegate].
 */
class CountingQueueManager(private val delegate: QueueManager) : QueueManager by delegate {
    var dequeueCount = 0
        private set

    override fun dequeueDispatches(count: Int, processor: String): List<Dispatch> {
        dequeueCount++
        return delegate.dequeueDispatches(count, processor)
    }
}

class TestConsentManager : ConsentManager {
    override val tealiumPurposeExplicitlyBlocked: Boolean = false
    override val configuration: StateSubject<ConsentConfiguration?> = Observables.stateSubject(null)

    override fun applyConsent(dispatch: Dispatch): TrackResult =
        TrackResult.dropped(dispatch, "Not used: the events are stored directly.")
}

/**
 * Opens while the dispatches pending for a dispatcher are at least [threshold], reacting
 * synchronously to the [queueMetrics].
 */
class PendingAtLeastBarrier(
    private val queueMetrics: QueueMetrics,
    private val threshold: Int
) : Barrier {
    override fun onState(dispatcherId: String): Observable<BarrierState> =
        queueMetrics.queueSizePendingDispatch(dispatcherId)
            .map { if (it >= threshold) BarrierState.Open else BarrierState.Closed }
            .distinct()
}

/**
 * Opens while the dispatches in flight for a dispatcher are at most [maxInFlight], reacting
 * synchronously to the [queueManager].
 */
class InFlightAtMostBarrier(
    private val queueManager: QueueManager,
    private val maxInFlight: Int
) : Barrier {
    override fun onState(dispatcherId: String): Observable<BarrierState> =
        queueManager.inFlightCount(dispatcherId)
            .map { if (it <= maxInFlight) BarrierState.Open else BarrierState.Closed }
            .distinct()
}

// endregion

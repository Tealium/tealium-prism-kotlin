package com.tealium.prism.core.ktx

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.pubsub.Subject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SingleExtensionsTest {

    private lateinit var subject: Subject<TealiumResult<String>>
    private lateinit var single: Single<TealiumResult<String>>

    @Before
    fun setUp() {
        subject = Observables.replaySubject(1)
        single = subject.asSingle(Scheduler.SYNCHRONOUS)
    }

    // region await()

    @Test
    fun await_Returns_Value_On_Success() = runTest(UnconfinedTestDispatcher()) {
        subject.onNext(TealiumResult.success("hello"))

        val result = single.await()

        assertEquals("hello", result)
    }

    @Test
    fun await_Throws_On_Failure() = runTest(UnconfinedTestDispatcher()) {
        val exception = RuntimeException("something went wrong")
        subject.onNext(TealiumResult.failure(exception))

        var thrown: Throwable? = null
        try {
            single.await()
        } catch (e: Throwable) {
            thrown = e
        }

        assertNotNull(thrown)
        assertEquals(exception, thrown)
    }

    @Test
    fun await_Throws_IllegalStateException_When_Single_Completes_Without_Emitting() = runTest(UnconfinedTestDispatcher()) {
        var thrown: Throwable? = null
        val job = launch {
            try {
                single.await()
            } catch (e: Exception) {
                thrown = e
            }
        }

        subject.onComplete()
        job.join()

        assertNotNull(thrown)
        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun await_Suspends_Until_Value_Emitted() = runTest(UnconfinedTestDispatcher()) {
        var result: String? = null

        val job = launch {
            result = single.await()
        }

        // Value has not been emitted yet; coroutine should still be active.
        assertTrue(job.isActive)
        assertFalse(job.isCompleted)

        subject.onNext(TealiumResult.success("delayed"))

        job.join()
        assertEquals("delayed", result)
    }

    @Test
    fun await_Disposes_Subscription_On_Cancellation() = runTest(UnconfinedTestDispatcher()) {
        val job = launch {
            try {
                single.await()
            } catch (_: CancellationException) {
            }
        }

        // Cancel before emitting — subscription should be disposed.
        job.cancel()
        job.join()

        // Confirm the subject has no active subscribers.
        assertEquals(0, subject.count)
    }

    @Test
    fun await_Disposes_Subscription_After_Emission() = runTest(UnconfinedTestDispatcher()) {
        subject.onNext(TealiumResult.success("value"))
        single.await()

        assertEquals(0, subject.count)
    }

    // endregion

    // region awaitResult()

    @Test
    fun awaitResult_Returns_Success_Result() = runTest(UnconfinedTestDispatcher()) {
        subject.onNext(TealiumResult.success("hello"))

        val result = single.awaitResult()

        assertTrue(result.isSuccess)
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun awaitResult_Returns_Failure_Result_Without_Throwing() = runTest(UnconfinedTestDispatcher()) {
        val exception = RuntimeException("oops")
        subject.onNext(TealiumResult.failure(exception))

        val result = single.awaitResult()

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun awaitResult_Disposes_Subscription_After_Emission() = runTest(UnconfinedTestDispatcher()) {
        subject.onNext(TealiumResult.success("value"))
        single.awaitResult()

        assertEquals(0, subject.count)
    }

    @Test
    fun awaitResult_Disposes_Subscription_On_Cancellation() = runTest(UnconfinedTestDispatcher()) {
        val job = launch {
            try {
                single.awaitResult()
            } catch (_: CancellationException) {
            }
        }

        job.cancel()
        job.join()

        assertEquals(0, subject.count)
    }

    // endregion
}

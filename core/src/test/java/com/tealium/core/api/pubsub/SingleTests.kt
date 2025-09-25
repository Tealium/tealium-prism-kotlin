package com.tealium.core.api.pubsub

import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.misc.TealiumResult
import com.tealium.tests.common.SynchronousScheduler
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SingleTests {

    private lateinit var subject: Subject<TealiumResult<Boolean>>
    private lateinit var single: Single<TealiumResult<Boolean>>

    @Before
    fun setUp() {
        subject = Observables.replaySubject(1)
        single = subject.asSingle(SynchronousScheduler())
    }

    @Test
    fun subscribe_Self_Disposes_After_Emission() {
        val verifier = mockk<TealiumCallback<Boolean>>(relaxed = true)
        subject.onNext(TealiumResult.success(true))

        single.onSuccess(verifier)

        assertEquals(0, subject.count)
    }

    @Test
    fun subscribe_Emits_Only_One_Value() {
        val verifier = mockk<Observer<TealiumResult<Boolean>>>(relaxed = true)
        val result = TealiumResult.success(true)
        subject.onNext(result)
        subject.onNext(result)

        single.subscribe(verifier)
        subject.onNext(result)
        subject.onNext(result)

        verify(exactly = 1) {
            verifier.onNext(result)
        }
    }

    @Test
    fun disposable_Disposes_Subscription() {
        val verifier = mockk<Observer<TealiumResult<Boolean>>>(relaxed = true)
        val result = TealiumResult.success(true)

        val disposable = single.subscribe(verifier)
        disposable.dispose()
        subject.onNext(result)

        verify {
            verifier wasNot Called
        }
    }

    @Test
    fun onSuccess_Receives_Result_When_Successful() {
        val verifier = mockk<TealiumCallback<Boolean>>(relaxed = true)
        subject.onNext(TealiumResult.success(true))

        single.onSuccess(verifier)

        verify {
            verifier.onComplete(true)
        }
    }

    @Test
    fun onSuccess_Receives_Nothing_When_Failed() {
        val verifier = mockk<TealiumCallback<Boolean>>(relaxed = true)
        subject.onNext(TealiumResult.failure(Exception()))

        single.onSuccess(verifier)

        verify {
            verifier wasNot Called
        }
    }

    @Test
    fun onFailure_Receives_Exception_When_Failure() {
        val verifier = mockk<TealiumCallback<Exception>>(relaxed = true)
        val ex = Exception()
        subject.onNext(TealiumResult.failure(ex))

        single.onFailure(verifier)

        verify {
            verifier.onComplete(ex)
        }
    }

    @Test
    fun onFailure_Receives_Nothing_When_Success() {
        val verifier = mockk<TealiumCallback<Exception>>(relaxed = true)
        subject.onNext(TealiumResult.success(true))

        single.onFailure(verifier)

        verify {
            verifier wasNot Called
        }
    }
}
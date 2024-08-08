package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class WithStateTests {

    @Test
    fun withState_Updates_State_From_Upstream_Emissions() {
        val subject = Observables.stateSubject(1)
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val withState = subject.withState(subject::value)
        withState.subscribe(onNext)

        assertEquals(1, withState.value)
        verify {
            onNext(1)
        }
        confirmVerified(onNext)
    }

    @Test
    fun withState_Gets_Initial_State_From_Provider_When_Not_Subscribed() {
        val subject = Observables.stateSubject(1)
        val withState = subject.withState(subject::value)

        subject.onNext(2)
        assertEquals(2, withState.value)
        subject.onNext(3)
        assertEquals(3, withState.value)
    }

    @Test
    fun withState_Does_Not_Call_Value_Provider_() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)
        val valueProvider = mockk<() -> Int>(relaxed = true)

        val subject = Observables.stateSubject(1)
        val withState = subject.withState(valueProvider)
        withState.subscribe(onNext)

        verify {
            onNext(1)
        }
        verify(inverse = true) {
            valueProvider()
        }
    }
}
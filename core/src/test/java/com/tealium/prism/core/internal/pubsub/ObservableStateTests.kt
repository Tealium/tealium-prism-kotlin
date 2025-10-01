package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertSubscriberCount
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class ObservableStateTests {

    @Test
    fun subscribe_Is_Delegated() {
        val observer = mockk<Observer<String>>(relaxed = true)
        val subject = Observables.stateSubject("test")
        val observableState = subject.asObservableState()

        observableState.subscribe(observer)

        subject.assertSubscriberCount(1)
    }

    @Test
    fun subscribe_Emits_All_From_Delegate() {
        val observer = mockk<Observer<String>>(relaxed = true)
        val subject = Observables.stateSubject("test")
        val observableState = subject.asObservableState()

        observableState.subscribe(observer)
        subject.onNext("updated")

        verify {
            observer.onNext("test")
            observer.onNext("updated")
        }
    }

    @Test
    fun value_Is_Delegated() {
        val subject = Observables.stateSubject("test")
        val observableState = subject.asObservableState()

        assertEquals(subject.value, observableState.value)
    }

    @Test
    fun value_Reflects_Latest_Value_Of_Delegate() {
        val subject = Observables.stateSubject("test")
        val observableState = subject.asObservableState()

        subject.onNext("updated")

        assertEquals(subject.value, observableState.value)
    }

    @Test(expected = ClassCastException::class)
    fun stateSubject_asObservableState_Cannot_Be_Cast_Back_To_Subject() {
        val observableState = Observables.stateSubject("test").asObservableState()

        observableState as StateSubject<String>
    }
}
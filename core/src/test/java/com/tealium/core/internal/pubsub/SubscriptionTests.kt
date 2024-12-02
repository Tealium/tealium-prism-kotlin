package com.tealium.core.internal.pubsub

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SubscriptionTests {

    @RelaxedMockK
    private lateinit var onDispose: () -> Unit
    private lateinit var subscription: Subscription

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        subscription = Subscription(onDispose)
    }

    @Test
    fun dispose_Executes_OnDispose_When_Not_Disposed() {
        subscription.dispose()

        verify {
            onDispose.invoke()
        }
    }

    @Test
    fun dispose_Does_Not_Execute_OnDispose_When_Already_Disposed() {
        subscription.dispose()
        subscription.dispose()
        subscription.dispose()

        verify(exactly = 1) {
            onDispose.invoke()
        }
    }

    @Test
    fun dispose_Sets_IsDisposed_True() {
        assertFalse(subscription.isDisposed)

        subscription.dispose()

        assertTrue(subscription.isDisposed)
    }
}
package com.tealium.prism.core.internal.pubsub

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedDisposableTest {

    @Test
    fun isDisposed_Is_Always_True() {
        assertTrue(CompletedDisposable.isDisposed)
    }

    @Test
    fun dispose_Does_Nothing() {
        CompletedDisposable.dispose()

        assertTrue(CompletedDisposable.isDisposed)
    }

    @Test
    fun only_A_Single_Instance() {
        assertSame(CompletedDisposable, CompletedDisposable)
    }
}
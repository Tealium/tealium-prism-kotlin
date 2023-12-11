package com.tealium.core.internal

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class FlowTests {

    val testFlow = flowOf(1, 2, 3)

    @RelaxedMockK
    lateinit var mockCallback: (Any) -> Unit

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun flatMap_Returns_NewFlow_FlatteningIt() = runTest {
        testFlow.flatMap {
            testFlow
        }.collect {
            mockCallback.invoke(it)
        }

        verify(exactly = 3) {
            mockCallback.invoke(1)
            mockCallback.invoke(2)
            mockCallback.invoke(3)
        }
    }

    @Test
    fun flatMap_Emits_AllValues_FromAllFlows() = runTest {
        testFlow.flatMap {
            delay(10)
            testFlow
        }.collect {
            mockCallback.invoke(it)
        }

        verify(exactly = 3) {
            mockCallback.invoke(1)
            mockCallback.invoke(2)
            mockCallback.invoke(3)
        }
    }

    @Test
    fun flatMapLatest_OnlyEmits_EmissionsFromLatestInput() = runTest {
        testFlow.flatMapLatest {
            flowOf(it, it, it)
        }.collect {
            mockCallback.invoke(it)
        }

        verify {
            mockCallback.invoke(1)
            mockCallback.invoke(2)
            mockCallback.invoke(3)
            mockCallback.invoke(3)
            mockCallback.invoke(3)
        }
    }

    @Test
    fun flatMapLatest_CancelsDownstream_IfNotProcessedQuickEnough() = runTest {
        testFlow.flatMapLatest {
            delay(10)
            flowOf(it)
        }.collect {
            delay(20)
            mockCallback.invoke(it)
        }

        verify(exactly = 1) {
            mockCallback.invoke(3)
        }
    }

    @Test
    fun flatten_EmitsAllEmissions_FromAllFlows() = runTest {
        testFlow.map {
            testFlow
        }.flatten().collect {
            mockCallback.invoke(it)
        }

        verify(exactly = 3) {
            mockCallback.invoke(1)
            mockCallback.invoke(2)
            mockCallback.invoke(3)
        }
    }

    @Test
    fun mapLatest_EmitsAllEmissions_FromAllFlows() = runTest {
        testFlow.mapLatest {
            it
        }.collect {
            mockCallback.invoke(it)
        }

        verify {
            mockCallback.invoke(1)
            mockCallback.invoke(2)
            mockCallback.invoke(3)
        }
    }

    @Test
    fun mapLatest_CancelsDownstream_IfNotProcessedQuickEnough() = runTest {
        testFlow.mapLatest {
            delay(10)
            it
        }.collect {
            delay(20)
            mockCallback.invoke(it)
        }

        verify {
            mockCallback.invoke(3)
        }
    }
}
package com.tealium.tests.common

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.Schedulers
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.internal.TealiumImpl
import com.tealium.prism.core.internal.TealiumProxy
import com.tealium.prism.core.internal.misc.SchedulersImpl
import com.tealium.prism.core.internal.misc.SingleThreadedScheduler
import com.tealium.prism.core.internal.misc.ThreadPoolScheduler
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

val testMainScheduler = SingleThreadedScheduler("test-main")
val testTealiumScheduler = SingleThreadedScheduler("tealium-test")
val testNetworkScheduler = ThreadPoolScheduler(0)
val testSchedulers: Schedulers = SchedulersImpl(testMainScheduler, testTealiumScheduler, testNetworkScheduler)

/**
 * Convenience method that awaits the execution of the [onReady] callback before returning it.
 */
fun createTealiumProxy(
    config: TealiumConfig,
    tealiumScheduler: Scheduler = testTealiumScheduler,
    onTealiumImplReady: Observable<TealiumResult<TealiumImpl>>,
    onShutdown: (String) -> Unit
): Tealium {
        return TealiumProxy(
            config.key,
            tealiumScheduler,
            onTealiumImplReady,
            onShutdown
        )
}

/**
 * Mocks all [DataStore.Editor] methods to make sure it returns the same mock for recording purposes
 */
fun mockkEditor(editor: DataStore.Editor = mockk()): DataStore.Editor {
    every { editor.putAll(any(), any()) } returns editor
    every { editor.put(any(), any<DataItem>(), any()) } returns editor
    every { editor.put(any(), any<String>(), any()) } returns editor
    every { editor.put(any(), any<Int>(), any()) } returns editor
    every { editor.put(any(), any<Long>(), any()) } returns editor
    every { editor.put(any(), any<Double>(), any()) } returns editor
    every { editor.put(any(), any<Boolean>(), any()) } returns editor
    every { editor.put(any(), any<DataList>(), any()) } returns editor
    every { editor.put(any(), any<DataObject>(), any()) } returns editor
    every { editor.remove(any<String>()) } returns editor
    every { editor.remove(any<List<String>>()) } returns editor
    every { editor.clear() } returns editor
    every { editor.commit() } just Runs

    return editor
}
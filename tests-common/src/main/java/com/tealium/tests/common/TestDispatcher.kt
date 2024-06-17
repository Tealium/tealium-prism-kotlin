package com.tealium.tests.common

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.core.internal.observables.CompletedDisposable
import io.mockk.spyk

class TestDispatcher(
    override val name: String,
    override val version: String = "1.0",
    override val dispatchLimit: Int = 1,
    private val dispatchHandler: ((List<Dispatch>, TealiumCallback<List<Dispatch>>) -> Disposable) = { dispatches, callback ->
        callback.onComplete(dispatches)
        CompletedDisposable
    }
) : Dispatcher {
    override fun dispatch(
        dispatches: List<Dispatch>,
        callback: TealiumCallback<List<Dispatch>>
    ): Disposable = dispatchHandler.invoke(dispatches, callback)


    companion object {
        fun mock(
            name: String,
            version: String = "1.0",
            dispatchLimit: Int = 1,
            dispatchHandler: ((List<Dispatch>, TealiumCallback<List<Dispatch>>) -> Disposable)? = null
        ): TestDispatcher {
            return if (dispatchHandler == null)
                spyk(TestDispatcher(name, version, dispatchLimit))
            else spyk(TestDispatcher(name, version, dispatchLimit, dispatchHandler))
        }
    }
}
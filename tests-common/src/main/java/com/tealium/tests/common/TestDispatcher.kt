package com.tealium.tests.common

import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import io.mockk.spyk

class TestDispatcher(
    override val id: String,
    override val version: String = "1.0",
    override val dispatchLimit: Int = 1,
    private val dispatchHandler: ((List<Dispatch>, Callback<List<Dispatch>>) -> Disposable) = { dispatches, callback ->
        callback.onComplete(dispatches)
        CompletedDisposable
    }
) : Dispatcher {
    override fun dispatch(
        dispatches: List<Dispatch>,
        callback: Callback<List<Dispatch>>
    ): Disposable = dispatchHandler.invoke(dispatches, callback)


    companion object {
        fun mock(
            name: String,
            version: String = "1.0",
            dispatchLimit: Int = 1,
            dispatchHandler: ((List<Dispatch>, Callback<List<Dispatch>>) -> Disposable)? = null
        ): TestDispatcher {
            return if (dispatchHandler == null)
                spyk(TestDispatcher(name, version, dispatchLimit))
            else spyk(TestDispatcher(name, version, dispatchLimit, dispatchHandler))
        }
    }
}
package com.tealium.tests.common

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import io.mockk.spyk

class TestDispatcher(
    override val name: String,
    override val version: String = "1.0",
    override val dispatchLimit: Int = 1,
    private val dispatchHandler: ((List<Dispatch>) -> Observable<List<Dispatch>>) = { dispatches ->
        Observables.just(
            dispatches
        )
    }
) : Dispatcher {
    override fun dispatch(dispatches: List<Dispatch>): Observable<List<Dispatch>> {
        return dispatchHandler.invoke(dispatches)
    }

    companion object {
        fun mock(
            name: String,
            version: String = "1.0",
            dispatchLimit: Int = 1,
            dispatchHandler: ((List<Dispatch>) -> Observable<List<Dispatch>>)? = null
        ): Dispatcher {
            return if (dispatchHandler == null)
                spyk(TestDispatcher(name, version, dispatchLimit))
            else spyk(TestDispatcher(name, version, dispatchLimit, dispatchHandler))
        }
    }
}
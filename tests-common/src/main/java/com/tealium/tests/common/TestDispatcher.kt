package com.tealium.tests.common

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class TestDispatcher(
    override val name: String,
    override val version: String = "1.0",
    private val dispatchHandler: ((List<Dispatch>) -> Unit)? = null
) : Dispatcher {
    override fun dispatch(dispatches: List<Dispatch>): Flow<List<Dispatch>> {
        dispatchHandler?.invoke(dispatches)
        return flowOf(dispatches)
    }
}
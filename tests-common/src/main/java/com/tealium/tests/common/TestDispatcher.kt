package com.tealium.tests.common

import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher

class TestDispatcher(
    override val name: String,
    override val version: String = "1.0",
    private val dispatchHandler: ((List<Dispatch>) -> Unit)? = null
) : Dispatcher {
    override fun dispatch(dispatches: List<Dispatch>) {
        dispatchHandler?.invoke(dispatches)
    }
}
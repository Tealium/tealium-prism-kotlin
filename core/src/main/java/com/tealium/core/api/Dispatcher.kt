package com.tealium.core.api

interface Dispatcher {
    fun dispatch(dispatches: List<Dispatch>)
}
package com.tealium.core.internal.network

sealed class DelayPolicy {
    abstract fun shouldDelay() : Boolean
}

class DoNotDelay : DelayPolicy() {
    override fun shouldDelay(): Boolean {
        return false
    }
}

class AfterDelay(val interval: Long) : DelayPolicy() {
    // completion for next step (reprocess request or ...) after delay is finished
    override fun shouldDelay(): Boolean {
        return true
    }
}

class AfterEvent(val event: Any) : DelayPolicy() {
    override fun shouldDelay(): Boolean {
        return true
    }
}

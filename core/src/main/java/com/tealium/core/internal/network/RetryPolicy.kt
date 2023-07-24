package com.tealium.core.internal.network

sealed class RetryPolicy {
    abstract fun shouldRetry() : Boolean
}

class DoNotRetry : RetryPolicy() {
    override fun shouldRetry() : Boolean {
        return false
    }
}
class RetryAfterDelay(val interval: Long) : RetryPolicy() {
    override fun shouldRetry() : Boolean {
        return true
    }
}
class RetryAfterEvent(val event: Any) : RetryPolicy() {
    override fun shouldRetry() : Boolean {
        return true
    }
}

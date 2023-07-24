package com.tealium.core.internal.network

import java.io.IOException

sealed class NetworkError {
    abstract fun isRetryable() : Boolean
}

class Non200Error(
    val statusCode: Int
): NetworkError() {
    override fun isRetryable(): Boolean {
        // inclusive range?? might need updating
        return statusCode == 429 || (500.. 600).contains(statusCode)
    }
}

class IOError(
    val ex: IOException?
): NetworkError() {
    override fun isRetryable(): Boolean {
        return true
    }
}

class UnexpectedError(
    val ex: Throwable?
): NetworkError() {
    override fun isRetryable(): Boolean {
        return false
    }
}

class Cancelled: NetworkError() {
    override fun isRetryable(): Boolean {
        return false
    }
}
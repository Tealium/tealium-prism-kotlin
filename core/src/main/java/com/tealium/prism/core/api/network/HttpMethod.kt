package com.tealium.prism.core.api.network

/**
 * Simple data class to hold the Http Method name of an Http request.
 *
 * There are two constant values available for [Get] and [Post] since they are most commonly used
 * in this library.
 */
data class HttpMethod(val value: String) {
    companion object {

        /**
         * Constant value for an Http GET request.
         */
        @JvmField
        val Get: HttpMethod = HttpMethod("GET")

        /**
         * Constant value for an Http POST request.
         */
        @JvmField
        val Post: HttpMethod = HttpMethod("POST")
    }
}
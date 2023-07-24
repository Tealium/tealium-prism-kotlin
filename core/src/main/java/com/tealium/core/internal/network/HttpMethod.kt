package com.tealium.core.internal.network

data class HttpMethod(val value: String) {
    companion object {

        @JvmField
        val Get: HttpMethod = HttpMethod("GET")

        @JvmField
        val Post: HttpMethod = HttpMethod("POST")
    }
}
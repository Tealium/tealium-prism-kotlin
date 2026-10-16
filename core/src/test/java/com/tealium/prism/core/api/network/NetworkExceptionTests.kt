package com.tealium.prism.core.api.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkExceptionTests {

    @Test
    fun isRetryable_Returns_True_When_Non200_Status_Is_Retryable() {
        for (statusCode in RETRYABLE_STATUS_CODES) {
            assertTrue(
                "Expected status $statusCode to be retryable",
                non200(statusCode).isRetryable()
            )
        }
    }

    @Test
    fun isRetryable_Returns_False_When_Non200_Status_Is_Not_Retryable() {
        for (statusCode in NON_RETRYABLE_STATUS_CODES) {
            assertFalse(
                "Expected status $statusCode not to be retryable",
                non200(statusCode).isRetryable()
            )
        }
    }

    @Test
    fun isRetryable_Returns_True_When_NetworkIOException() {
        assertTrue(NetworkException.NetworkIOException(IOException("io")).isRetryable())
    }

    @Test
    fun isRetryable_Returns_True_When_NetworkIOException_Has_No_Cause() {
        assertTrue(NetworkException.NetworkIOException(null).isRetryable())
    }

    @Test
    fun isRetryable_Returns_False_When_UnexpectedException() {
        assertFalse(NetworkException.UnexpectedException(RuntimeException("unexpected")).isRetryable())
    }

    @Test
    fun isRetryable_Returns_False_When_CancelledException() {
        assertFalse(NetworkException.CancelledException.isRetryable())
    }

    private fun non200(statusCode: Int): NetworkException.Non200Exception =
        NetworkException.Non200Exception(statusCode)

    private companion object {
        val RETRYABLE_STATUS_CODES = listOf(408, 429, 500, 502, 503, 504)
        val NON_RETRYABLE_STATUS_CODES = listOf(304, 400, 401, 403, 404, 407, 409, 499, 501, 505, 599, 600)
    }
}

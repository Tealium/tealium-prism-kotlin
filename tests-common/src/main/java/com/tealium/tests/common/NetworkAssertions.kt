package com.tealium.tests.common

import com.tealium.prism.core.api.misc.TimeFrameUtils.inMilliseconds
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.RetryPolicy
import org.junit.Assert.assertTrue

/**
 * Asserts that [result] is a [NetworkResult.Failure] of [NetworkException.CancelledException].
 */
fun assertCancelled(result: NetworkResult?) = assertTrue(
    "Expected Failure(CancelledException), got $result",
    result is NetworkResult.Failure && result.networkException === NetworkException.CancelledException
)

/**
 * Asserts that [policy] is a [RetryPolicy.AfterDelay], and returns its delay in milliseconds.
 */
fun assertAfterDelay(policy: RetryPolicy): Long {
    assertTrue("Expected a RetryPolicy.AfterDelay, got $policy", policy is RetryPolicy.AfterDelay)
    return (policy as RetryPolicy.AfterDelay).delay.inMilliseconds()
}

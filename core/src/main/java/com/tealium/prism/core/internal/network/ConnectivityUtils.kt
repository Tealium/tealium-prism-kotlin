package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.Connectivity

/**
 * Whether sending should be attempted for this status.
 *
 * Only [Connectivity.Status.NotConnected] rules out sending. [Connectivity.Status.Unknown] means
 * there is no information, so the SDK does not hold back sending for it.
 */
internal val Connectivity.Status.isAssumedConnected: Boolean
    get() = this !is Connectivity.Status.NotConnected

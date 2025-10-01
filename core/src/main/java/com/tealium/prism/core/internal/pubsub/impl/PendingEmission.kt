package com.tealium.prism.core.internal.pubsub.impl

/**
 * Utility wrapper to support differentiating between no emissions yet, and `null` emissions
 */
class PendingEmission<T>(val value: T)
package com.tealium.core.internal.consent

import com.tealium.core.api.consent.ConsentDecision

// TODO - stub only so far
fun ConsentDecision.matchAll(requiredPurposes: Set<String>): Boolean {
    return purposes.containsAll(requiredPurposes)
}
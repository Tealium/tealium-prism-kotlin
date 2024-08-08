package com.tealium.core.internal.modules.consent

import com.tealium.core.api.modules.consent.ConsentDecision

// TODO - stub only so far
fun ConsentDecision.matchAll(requiredPurposes: Set<String>): Boolean {
    return purposes.containsAll(requiredPurposes)
}
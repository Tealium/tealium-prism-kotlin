package com.tealium.prism.core.internal.command

import com.tealium.prism.core.api.tracking.Dispatch

/**
 * Extracts command names from the dispatch payload.
 * Supports both a DataList of strings (multiple commands) and a single String,
 * matching the behaviour of the Swift Dispatch+Commands extension.
 */
internal fun Dispatch.getCommands(): List<String> {
    val payload = payload()
    return payload.getDataList(Dispatch.Keys.COMMAND_NAME)
        ?.mapNotNull { it.getString() }
        ?.filter { it.isNotBlank() }
        ?: listOfNotNull(payload.getString(Dispatch.Keys.COMMAND_NAME)?.takeIf { it.isNotBlank() })
}

@file:JvmName("DispatchCommandUtils")

package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.command.CommandRegistry

/**
 * Extracts command names from the dispatch payload.
 * Supports both a DataList of strings (multiple commands) and a single String,
 * matching the behavior of the Swift Dispatch+Commands extension.
 *
 * The names are returned normalized: trimmed of whitespace and lowercased, which is how commands are matched
 * when executed. Blank and non-String commands are skipped, and duplicates are removed, keeping the first
 * occurrence and preserving the order.
 * The payload's "command_name" is left unchanged, so anything reading it directly may see the original
 * names, including duplicates.
 *
 * @return the list of unique, normalized command names; empty if there are none.
 */
fun Dispatch.getCommands(): List<String> {
    val payload = payload()

    val commands: List<String> =
        payload.getDataList(Dispatch.Keys.COMMAND_NAME)?.mapNotNull(DataItem::getString)
            ?: payload.getString(Dispatch.Keys.COMMAND_NAME)?.let { listOf(it) }
            ?: return emptyList()

    return commands.map(CommandRegistry::normalize)
        .filter(String::isNotEmpty)
        .distinct()
}

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
 * Blank and non-String commands are ignored, and duplicate commands are removed, keeping the first occurrence and
 * preserving the order. Two commands are duplicates if they are equal once trimmed of whitespace and
 * lowercased, which is how commands are matched when executed.
 * Duplicates are removed only from the returned list: the payload's "command_name" is left unchanged,
 * so anything reading it directly may still see duplicates.
 *
 * @return the list of unique command names; empty if there are none.
 */
fun Dispatch.getCommands(): List<String> {
    val payload = payload()

    val commands: List<String> =
        payload.getDataList(Dispatch.Keys.COMMAND_NAME)?.mapNotNull(DataItem::getString)
            ?: payload.getString(Dispatch.Keys.COMMAND_NAME)?.let { listOf(it) }
            ?: return emptyList()

    return commands.filter(String::isNotBlank)
        .distinctBy(CommandRegistry::normalize)
}

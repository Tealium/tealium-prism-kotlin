package com.tealium.prism.core.internal.logger

import com.tealium.prism.core.api.tracking.Dispatch

/**
 * Returns a [Throwable]'s message, or the [default] provided.
 *
 * If no [default] is provided then the string "null" will be returned.
 */
fun Throwable.nonNullMessage(default: String = "null"): String {
    return message ?: default
}

/**
 * Maps a [Collection] of [Dispatch]es to a list of their [Dispatch.logDescription] values
 */
fun Collection<Dispatch>.logDescriptions(): List<String> =
    map(Dispatch::logDescription)

/**
 * Maps a [Collection] of [Dispatch]es to a list of their [Dispatch.id] values
 */
fun Collection<Dispatch>.ids(): List<String> =
    map(Dispatch::id)

/**
 * This message with [cause]'s stack trace appended: the message, a line break, then the standard
 * [Throwable.printStackTrace] output, including its `Caused by:` lines for each cause in the chain.
 *
 * That is the shape [android.util.Log] itself gives a throwable, so SDK logs read like the
 * platform's own and the cause chain, which is usually where the real reason is, is not lost.
 *
 * TODO: [Logger][com.tealium.prism.core.api.logger.Logger] carries only a String, so the trace
 *  travels inside the message. The default handler forwards it to the two-argument `Log.e`, whose
 *  single payload is capped at about 4000 bytes; a long trace loses its tail there, where the
 *  `Caused by:` lines are. The three-argument `Log.e` splits across entries instead. Fixing this
 *  means a throwable on `LogHandler`, at which point this helper goes away.
 *
 * Not a `Logger.error(category, message, cause)` extension: the member
 * `error(category, message, vararg args)` would win overload resolution and swallow the cause.
 */
internal fun String.withCause(cause: Throwable): String = this + "\n" + cause.stackTraceToString()

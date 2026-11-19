package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.data.ExtractionException
import com.tealium.prism.core.api.misc.TealiumException

/**
 * Represents a failure that occurred during [Command] execution.
 */
class CommandException(
    message: String? = null,
    cause: Throwable? = null
) : TealiumException(message, cause) {

    companion object {

        /**
         * A payload parameter could not be read.
         *
         * The message is the [cause]'s message; the [cause] tells which parameter failed and why.
         */
        @JvmStatic
        fun invalidPayload(cause: ExtractionException): CommandException =
            CommandException(cause.message, cause)

        // The factories below are kept because dispatchers built against 0.7.0 call them, and
        // removing them would fail at runtime with NoSuchMethodError.
        // They keep their original messages and carry no cause, like the deprecated cases on iOS.

        /** A required parameter was absent from the payload. */
        @Deprecated(
            "Deprecated, will be removed in a future version. Use invalidPayload(ExtractionException.Missing(key)) instead.",
            ReplaceWith(
                "CommandException.invalidPayload(ExtractionException.Missing(key))",
                "com.tealium.prism.core.api.data.ExtractionException"
            )
        )
        @JvmStatic
        fun missingParameter(key: String): CommandException =
            CommandException("Required parameter missing: $key")

        /** A parameter was present but did not match the expected type. */
        @Deprecated(
            "Deprecated, will be removed in a future version. Use invalidPayload(ExtractionException.TypeMismatch(key, expectedType)) instead.",
            ReplaceWith(
                "CommandException.invalidPayload(ExtractionException.TypeMismatch(key, expectedType))",
                "com.tealium.prism.core.api.data.ExtractionException"
            )
        )
        @JvmStatic
        fun invalidParameterType(key: String, expectedType: String): CommandException =
            CommandException("Parameter '$key' expected type $expectedType")

        /** A parameter was present but its value was empty. */
        @Deprecated(
            "Deprecated, will be removed in a future version. Use invalidPayload(ExtractionException.Empty(key)) instead.",
            ReplaceWith(
                "CommandException.invalidPayload(ExtractionException.Empty(key))",
                "com.tealium.prism.core.api.data.ExtractionException"
            )
        )
        @JvmStatic
        fun emptyParameter(key: String): CommandException =
            CommandException("Parameter '$key' is empty")

        /** An array parameter was present but contained no elements. */
        @Deprecated(
            "Deprecated, will be removed in a future version. Use invalidPayload(ExtractionException.Empty(key)) instead.",
            ReplaceWith(
                "CommandException.invalidPayload(ExtractionException.Empty(key))",
                "com.tealium.prism.core.api.data.ExtractionException"
            )
        )
        @JvmStatic
        fun emptyArray(key: String): CommandException =
            CommandException("Array '$key' is empty")

        /** Two related array parameters had different lengths and could not be paired. */
        @JvmStatic
        fun arrayLengthMismatch(keyA: String, keyB: String) =
            CommandException("Arrays '$keyA' and '$keyB' have different lengths")

        /**
         * None of the payload's parameters were usable by the command.
         *
         * When [expected] is not empty, its entries are listed in the message.
         */
        @JvmStatic
        @JvmOverloads
        fun noValidParameters(expected: List<String> = emptyList()): CommandException {
            var message = "No valid parameter found."
            if (expected.isNotEmpty()) {
                message += " Expected one of: ${expected.joinToString(", ") { "'$it'" }}"
            }
            return CommandException(message)
        }

        /** No command was registered under the given name. */
        @JvmStatic
        fun commandNotFound(commandName: String) =
            CommandException("Command not found: $commandName")

        /**
         * An unexpected throwable was caught during command execution.
         *
         * The message uses [Throwable.message], falling back to the class name when absent. The class
         * name is not used otherwise because it is obfuscated in release builds; the type is still
         * available through [cause].
         */
        @JvmStatic
        fun unknown(cause: Throwable) =
            CommandException("Command failed due to error: ${cause.message ?: cause.javaClass.name}", cause)
    }
}

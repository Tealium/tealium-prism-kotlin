package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.misc.TealiumException

/**
 * Represents a failure that occurred during [Command] execution.
 */
class CommandException(
    message: String? = null,
    cause: Throwable? = null
) : TealiumException(message, cause) {

    companion object {

        /** A required parameter was absent from the payload. */
        @JvmStatic
        fun missingParameter(key: String) =
            CommandException("Required parameter missing: $key")

        /** A parameter was present but did not match the expected type. */
        @JvmStatic
        fun invalidParameterType(key: String, expectedType: String) =
            CommandException("Parameter '$key' expected type $expectedType")

        /** A parameter was present but its value was empty. */
        @JvmStatic
        fun emptyParameter(key: String) =
            CommandException("Parameter '$key' is empty")

        /** An array parameter was present but contained no elements. */
        @JvmStatic
        fun emptyArray(key: String) =
            CommandException("Array '$key' is empty")

        /** Two related array parameters had different lengths and could not be paired. */
        @JvmStatic
        fun arrayLengthMismatch(keyA: String, keyB: String) =
            CommandException("Arrays '$keyA' and '$keyB' have different lengths")

        /** None of the payload's parameters were usable by the command. */
        @JvmStatic
        fun noValidParameters() =
            CommandException("No valid parameters found")

        /** No command was registered under the given name. */
        @JvmStatic
        fun commandNotFound(commandName: String) =
            CommandException("Command not found: $commandName")

        /** An unexpected throwable was caught during command execution. */
        @JvmStatic
        fun unknown(cause: Throwable) =
            CommandException(cause.message, cause)
    }
}

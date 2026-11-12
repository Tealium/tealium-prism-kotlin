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

        /**
         * A parameter was present with the correct type, but its value is not acceptable.
         *
         * [reason] states what is accepted (for example `expected one of granted, denied`) and must
         * not include the received value: command errors are logged and values can be personal data.
         */
        @JvmStatic
        fun invalidParameterValue(key: String, reason: String) =
            CommandException("Invalid value for '$key' - $reason")

        /** An array parameter was present but contained no elements. */
        @JvmStatic
        fun emptyArray(key: String) =
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

package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.misc.TealiumException

/**
 * A value could not be read from a [DataObject], such as a command payload.
 *
 * [key] is the rendered path of the parameter. Messages never include the received value:
 * they are logged and values can be personal data.
 *
 * @see com.tealium.prism.core.api.command.CommandException.invalidPayload
 */
sealed class ExtractionException(
    val key: String,
    message: String
) : TealiumException(message) {

    /** No value exists at [key], or it is an explicit `null`. */
    class Missing(key: String) :
        ExtractionException(key, "Missing required value for '$key'")

    /** The value at [key] cannot be converted to [expectedType]. */
    class TypeMismatch(key: String, val expectedType: String) :
        ExtractionException(key, "Invalid type for '$key' - expected $expectedType")

    /**
     * The value at [key] has the right type but is not acceptable.
     *
     * [reason] states what is accepted (for example `expected one of granted, denied`) and must
     * not include the received value.
     */
    class InvalidValue(key: String, val reason: String) :
        ExtractionException(key, "Invalid value for '$key' - $reason")

    /** The value at [key] is a blank string, an empty array or an empty object. */
    class Empty(key: String) :
        ExtractionException(key, "Empty value for '$key'")
}

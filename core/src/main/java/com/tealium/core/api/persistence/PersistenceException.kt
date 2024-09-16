package com.tealium.core.api.persistence

import com.tealium.core.api.misc.TealiumIOException

/**
 * Exception indicating that an error has occurred during an update event, e.g. insert/update/delete
 */
class PersistenceException(
    message: String,
    cause: Throwable
) : TealiumIOException(message, cause)

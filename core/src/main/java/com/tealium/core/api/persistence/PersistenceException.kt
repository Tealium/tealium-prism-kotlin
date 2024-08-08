package com.tealium.core.api.persistence

/**
 * Exception indicating that an error has occurred during an update event, e.g. insert/update/delete
 */
class PersistenceException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)

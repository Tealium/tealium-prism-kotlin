package com.tealium.prism.core.internal.modules.trace

import com.tealium.prism.core.api.data.DataObject

data class TraceModuleConfiguration(val trackErrors: Boolean = DEFAULT_TRACK_ERRORS) {
    companion object Companion {
        const val KEY_TRACK_ERRORS = "track_errors"
        const val DEFAULT_TRACK_ERRORS = false

        fun fromDataObject(dataObject: DataObject): TraceModuleConfiguration {
            val shouldTrackErrors = dataObject.getBoolean(KEY_TRACK_ERRORS)
                ?: DEFAULT_TRACK_ERRORS

            return TraceModuleConfiguration(
                shouldTrackErrors
            )
        }
    }
}

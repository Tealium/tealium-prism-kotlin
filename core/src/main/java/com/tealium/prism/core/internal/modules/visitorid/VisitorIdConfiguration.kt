package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.api.data.DataObject

/**
 * The configuration for the visitor id module.
 *
 * @param visitorIdentityKey The data layer key to watch for the current user's identity. Visitor
 *  switching is inactive while this is `null`.
 */
data class VisitorIdConfiguration(
    val visitorIdentityKey: String? = DEFAULT_VISITOR_IDENTITY_KEY
) {
    companion object {
        const val KEY_VISITOR_IDENTITY_KEY = "visitor_identity_key"
        val DEFAULT_VISITOR_IDENTITY_KEY: String? = null

        fun fromDataObject(dataObject: DataObject): VisitorIdConfiguration {
            val visitorIdentityKey = dataObject.getString(KEY_VISITOR_IDENTITY_KEY)
                ?: DEFAULT_VISITOR_IDENTITY_KEY

            return VisitorIdConfiguration(
                visitorIdentityKey
            )
        }
    }
}

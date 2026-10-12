package com.tealium.prism.core.api.settings.modules

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.modules.VisitorIdManager
import com.tealium.prism.core.internal.modules.visitorid.VisitorIdConfiguration

/**
 * A [ModuleSettingsBuilder] implementation to configure visitor ID handling, exposed at runtime
 * through [Tealium.visitorId].
 *
 * @see VisitorIdManager
 */
class VisitorIdSettingsBuilder :
    CollectorSettingsBuilder<VisitorIdSettingsBuilder>(Modules.Types.VISITOR_ID) {

    /**
     * Sets the data layer key that holds the current user's identity, enabling Visitor Switching.
     *
     * When an identity is written to this [key], the visitor id may switch, depending on whether the
     * current visitor id is owned and whether the identity has been seen before on this device:
     *  - while no identity owns the current visitor id:
     *    - a new identity takes over the current visitor id
     *    - a known identity restores the visitor id it was last associated with
     *  - once an identity owns the current visitor id:
     *    - a new identity is given a new anonymous visitor id
     *    - a known identity restores the visitor id it was last associated with
     *
     * Removing the [key] from the data layer does not change the visitor id. Only non-blank string
     * values are treated as identities.
     *
     * Identities are hashed before being stored, so the identity itself is never persisted.
     *
     * The default is `null`, which leaves Visitor Switching disabled.
     *
     * @param key The data layer key to read the identity from
     */
    fun setVisitorIdentityKey(key: String) = apply {
        configuration.put(VisitorIdConfiguration.KEY_VISITOR_IDENTITY_KEY, key)
    }
}

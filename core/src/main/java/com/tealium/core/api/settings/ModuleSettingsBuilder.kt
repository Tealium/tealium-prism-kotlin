package com.tealium.core.api.settings

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.modules.Module

/**
 * Base class to extend from when creating a settings builder for a new module.
 *
 * [Module] settings are expected to be [TealiumBundle]s and as such, this class becomes a wrapper
 * around a standard [TealiumBundle.Builder], but allows for implementations to provide more
 * user-friendly method names.
 *
 * Therefore it also doesn't require users to know which json key any particular setting needs to be set at.
 */
open class ModuleSettingsBuilder {
    protected val builder: TealiumBundle.Builder = TealiumBundle.Builder()

    /**
     * Sets the resulting [Module] to be permanently enabled/disabled. Local/Remote settings sources will
     * be overridden by this
     */
    fun setEnabled(enabled: Boolean) = apply {
        builder.put(KEY_ENABLED, enabled)
    }

    /**
     * Returns the complete [Module] settings as configured by this [ModuleSettingsBuilder].
     */
    fun build(): TealiumBundle = builder.getBundle()

    companion object {
        const val KEY_ENABLED = "enabled"
    }
}
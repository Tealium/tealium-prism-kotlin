package com.tealium.core.api.settings.modules

import com.tealium.core.api.modules.Module

/**
 * A settings builder capability that allows for custom Module Id's to be set, specifically to support
 * multiple instances of a given [Module] where unique Module Id's are required.
 */
interface MultipleInstancesModuleSettingsBuilder<T: ModuleSettingsBuilder<T>> {

    /**
     * Overrides the [Module.id] for cases where multiple instances of a [Module] are supported.
     *
     * The [moduleId] is required to be unique across all modules in the system, and where duplicates
     * are found, only the first will be used.
     */
    fun setModuleId(moduleId: String): T
}
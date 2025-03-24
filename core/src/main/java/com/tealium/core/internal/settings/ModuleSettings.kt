package com.tealium.core.internal.settings

import com.tealium.core.api.data.DataObject

class ModuleSettings(
    val enabled: Boolean = true,
    val configuration: DataObject = DataObject.EMPTY_OBJECT
) {
    constructor(dataObject: DataObject) : this(
        enabled = dataObject.getBoolean(KEY_ENABLED) ?: true,
        configuration = dataObject.getDataObject(KEY_CONFIGURATION) ?: DataObject.EMPTY_OBJECT
    )
    // TODO - applyRules: List<LoadRule>?
    // TODO - excludeRules: List<LoadRule>?
    // TODO - mappings: List<Mappings>?

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_CONFIGURATION = "configuration"
        const val KEY_APPLY_RULES = "applyRules"
        const val KEY_EXCLUDE_RULES = "excludeRules"
        const val KEY_MAPPINGS = "mappings"
    }
}
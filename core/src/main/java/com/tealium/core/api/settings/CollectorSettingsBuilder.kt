package com.tealium.core.api.settings

import com.tealium.core.api.modules.Collector

/**
 * A settings builder class to support building of settings relevant to [Collector] implementations.
 */
open class CollectorSettingsBuilder<T : CollectorSettingsBuilder<T>>(moduleType: String) :
    RuleModuleSettingsBuilder<T>(moduleType)
package com.tealium.prism.core.api.rules

/**
 * Indicates that a rule was configured for a module, but was not found. This will cause the module
 * feature (collection/dispatching etc) not to happen.
 */
class RuleNotFoundException(message: String, cause: Throwable? = null) :
    InvalidMatchException(message, cause) {

    constructor(
        ruleId: String,
        moduleId: String,
        cause: Throwable? = null
    ) : this("Rule not found for id $ruleId, configured for module $moduleId", cause)
}

package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule

val conditionConverter: Rule.Converter<Condition> =
    Rule.Converter(Condition.Converter)
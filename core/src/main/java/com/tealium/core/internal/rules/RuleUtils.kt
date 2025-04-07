package com.tealium.core.internal.rules

import com.tealium.core.api.rules.Condition
import com.tealium.core.api.rules.Rule

val conditionConverter: Rule.Converter<Condition> =
    Rule.Converter(Condition.Converter)
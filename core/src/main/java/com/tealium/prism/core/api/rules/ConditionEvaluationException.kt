package com.tealium.prism.core.api.rules

/**
 * Exception class to signify that a [Condition] has failed in an exceptional way during evaluation.
 *
 * It is therefore unable to return an accurate result.
 */
class ConditionEvaluationException(
    val condition: Condition,
    message: String,
    cause: Throwable? = null
) : InvalidMatchException(message, cause) {
    constructor(condition: Condition, cause: Throwable?): this(
        condition,
        buildString {
            append("Condition evaluation failed:\n")
            append(condition.asDataItem().toString())
            cause?.message?.let { msg ->
                append("\n\t Cause: ", msg)
            }
        },
        cause
    )
}
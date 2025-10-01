package com.tealium.prism.core.api.rules

import com.tealium.prism.core.api.rules.Matchable.Companion.matches

/**
 * [Matchable] is a generic interface for checking whether an input matches an expectation.
 */
fun interface Matchable<T> {

    /**
     * Returns whether or not the this [Matchable] matches the given [input].
     *
     * @param input The value to evaluate against
     * @return true if this [Matchable] matches, else false
     * @throws InvalidMatchException when the matchable is invalid or cannot be evaluated.
     */
    @Throws(InvalidMatchException::class)
    fun matches(input: T): Boolean

    companion object {
        /**
         * Utility method to provide a [Matchable] whose [matches] method always returns `false`
         *
         * @return A new [Matchable] whose [matches] method always returns `false`
         */
        @JvmStatic
        fun <T> notMatches(): Matchable<T> = Matchable { false }

        /**
         * Utility method to provide a [Matchable] whose [matches] method always returns `true`
         *
         * @return A new [Matchable] whose [matches] method always returns `true`
         */
        @JvmStatic
        fun <T> matches() = Matchable<T> { true }
    }
}
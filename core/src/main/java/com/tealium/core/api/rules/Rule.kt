@file:JvmName("RuleUtils")
package com.tealium.core.api.rules

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.UnsupportedDataItemException

/**
 * Simple Rule builder class for building out logical rules, that can be serialized to a [DataItem]
 * The type of [T] should therefore either implement [DataItemConvertible] or be one of the types
 * supported by [DataItem]
 *
 * The type [T] is the required input type, e.g. [Condition] for complex rules
 * or [String] for
 */
sealed class Rule<T> : DataItemConvertible {

    /**
     * Returns a new [Rule] that is the logical equivalent of the following:
     * ```kotlin
     * this || others
     * ```
     *
     * @param others The other [Rule]s to evaluate.
     * @return A new [Rule] representing the logical "or" of this rule and the given [others].
     */
    @SafeVarargs
    fun or(vararg others: Rule<T>): Rule<T> = OrRule(listOf(this, *others))

    /**
     * Returns a new [Rule] that is the logical equivalent of the following operation:
     * ```kotlin
     * this && others
     * ```
     *
     * @param others The other [Rule]s to evaluate.
     * @return A new [Rule] representing the logical "and" of this rule and the given [others].
     */
    @SafeVarargs
    fun and(vararg others: Rule<T>): Rule<T> = AndRule(listOf(this, *others))

    /**
     * Returns a new [Rule] that is the logical equivalent of the following operation:
     * ```kotlin
     * !this
     * ```
     *
     * @return A new [Rule] representing the logical "not" of this rule.
     */
    fun not(): Rule<T> = NotRule(this)

    companion object {

        /**
         * Returns a new [Rule] that represents a given value of [T], representing the logic
         * of the type [T].
         *
         * This method will not throw [UnsupportedDataItemException] directly, but it will when [asDataItem]
         * is called if the [expected] value does not implement [DataItemConvertible], or it not
         * supported by [DataItem]
         *
         * @see Condition
         *
         * @param expected The representation of this [Rule]'s logic
         * @return A new [Rule] with which to continue building out the [Rule]
         */
        @JvmStatic
        fun <T> just(expected: T): Rule<T> = JustRule(expected, DataItem::convert)

        /**
         * Returns a new [Rule] that represents a given value of [T], representing the logic
         * of the type [T].
         *
         * @see Condition
         *
         * @param expected The representation of this [Rule]'s logic
         * @param converter A converter to safely convert unsupported types when serializing to [DataItem]
         * @return A new [Rule] with which to continue building out the [Rule]
         */
        @JvmStatic
        fun <T> just(expected: T, converter: (T) -> DataItem): Rule<T> =
            JustRule(expected, converter)

        /**
         * Returns a new [Rule] that is the logical equivalent of the following operation:
         * ```kotlin
         * rules[0] && rules[1] && ...
         * ```
         *
         * @param rules The other [Rule]s to evaluate.
         * @return A new [Rule] representing the logical "and" of the given [rules].
         */
        @JvmStatic
        @SafeVarargs
        fun <T> all(vararg rules: Rule<T>): Rule<T> = all(rules.asList())

        /**
         * Returns a new [Rule] that is the logical equivalent of the following operation:
         * ```kotlin
         * rules[0] && rules[1] && ...
         * ```
         *
         * @param rules The other [Rule]s to evaluate.
         * @return A new [Rule] representing the logical "and" of the given [rules].
         */
        @JvmStatic
        fun <T> all(rules: List<Rule<T>>): Rule<T> = AndRule(rules)

        /**
         * Returns a new [Rule] that is the logical equivalent of the following operation:
         * ```kotlin
         * rules[0] || rules[1] || ...
         * ```
         *
         * @param rules The other [Rule]s to evaluate.
         * @return A new [Rule] representing the logical "or" of the given [rules].
         */
        @JvmStatic
        @SafeVarargs
        fun <T> any(vararg rules: Rule<T>): Rule<T> = any(rules.asList())

        /**
         * Returns a new [Rule] that is the logical equivalent of the following operation:
         * ```kotlin
         * rules[0] || rules[1] || ...
         * ```
         *
         * @param rules The other [Rule]s to evaluate.
         * @return A new [Rule] representing the logical "or" of the given [rules].
         */
        @JvmStatic
        fun <T> any(rules: List<Rule<T>>): Rule<T> = OrRule(rules)

        /**
         * Returns a new [Rule] that is the logical equivalent of the following operation:
         * ```kotlin
         * !rule
         * ```
         * @param rule The [Rule] to invert the result of.
         * @return A new [Rule] representing the logical "not" of the given [rule].
         */
        @JvmStatic
        fun <T> not(rule: Rule<T>): Rule<T> = NotRule(rule)

        const val OPERATOR_AND = "and"
        const val OPERATOR_OR = "or"
        const val OPERATOR_NOT = "not"

        const val KEY_CHILDREN = "children"
        const val KEY_OPERATOR = "operator"
    }

    /**
     * Takes a [Rule] implementation and converts it another [Rule], wrapping the type of [T]
     * in a [Matchable] so it can be evaluated.
     *
     * @param converter The class that converts a value of [T] to a [Matchable]
     */
    fun <R> asMatchable(converter: (T) -> Matchable<R>): Matchable<R> =
        asMatchable(this, converter)

    private fun <R> asMatchable(rule: Rule<T>, converter: (T) -> Matchable<R>): Matchable<R> =
        when (rule) {
            is AndRule -> Matchable { input ->
                rule.rules.map { asMatchable(it, converter) }
                    .all { it.matches(input) }
            }

            is OrRule -> Matchable { input ->
                rule.rules.map { asMatchable(it, converter) }
                    .any { it.matches(input) }
            }

            is NotRule -> Matchable { input ->
                !rule.rule.asMatchable(converter).matches(input)
            }

            is JustRule -> converter.invoke(rule.expected)
        }

    /**
     * [DataItemConverter] implementation that handles the re-creation of the [Rule] operators,
     * delegating the creation of the [T] to the provided [converter].
     */
    class Converter<T>(
        private val converter: DataItemConverter<T>
    ) : DataItemConverter<Rule<T>> {

        override fun convert(dataItem: DataItem): Rule<T>? {
            val condition = converter.convert(dataItem)
            if (condition != null) {
                return just(condition)
            }

            val dataObject = dataItem.getDataObject() ?: return null

            val operator = dataObject.getString(KEY_OPERATOR)
            val children = dataObject.getDataList(KEY_CHILDREN)
            if (operator == null || children == null) return null

            val mappedChildren = children.mapNotNull(::convert)

            return when (operator.lowercase()) {
                OPERATOR_AND -> all(*mappedChildren.toTypedArray())
                OPERATOR_OR -> any(*mappedChildren.toTypedArray())
                OPERATOR_NOT -> not(mappedChildren.first())
                else -> null
            }
        }
    }
}

fun <T, R> Rule<T>.matches(input: R): Boolean where T: Matchable<R> {
    return when (this) {
        is AndRule -> rules.all { it.matches(input) }
        is OrRule -> rules.any { it.matches(input) }
        is NotRule -> !rule.matches(input)
        is JustRule -> expected.matches(input)
    }
}

fun <T, R> Rule<T>.asMatchable(): Matchable<R> where T: Matchable<R> {
    return Matchable { input ->
        this.matches(input)
    }
}

private data class AndRule<T>(val rules: List<Rule<T>>) : Rule<T>() {
    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(KEY_OPERATOR, OPERATOR_AND)
            put(KEY_CHILDREN, rules.asDataList())
        }.asDataItem()
    }
}

private data class OrRule<T>(val rules: List<Rule<T>>) : Rule<T>() {
    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(KEY_OPERATOR, OPERATOR_OR)
            put(KEY_CHILDREN, rules.asDataList())
        }.asDataItem()
    }
}

private data class NotRule<T>(val rule: Rule<T>) : Rule<T>() {
    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(KEY_OPERATOR, OPERATOR_NOT)
            put(KEY_CHILDREN, DataList.create { add(rule) })
        }.asDataItem()
    }
}

private data class JustRule<T>(val expected: T, val converter: (T) -> DataItem) : Rule<T>() {
    override fun asDataItem(): DataItem {
        return converter.invoke(expected)
    }
}
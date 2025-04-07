package com.tealium.core.internal.rules

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.UnsupportedDataItemException
import com.tealium.core.api.rules.Condition
import com.tealium.core.api.rules.Condition.Operator

/**
 * Object for looking up standard Tealium [Condition.Operator] implementations.
 *
 * With the exception of [isDefined]/[isNotDefined] which explicitly check for the existence of values
 * at given keys, [Operator] implementations will not match in cases where the the [DataItem] was not
 * found in the payload,
 *
 * [Operator]s that require a filter value will
 *  - not match if the filter is `null`
 *  - not match if it requires a value of a given type but the value was not parseable as such
 *    (e.g. "greaterThan" requires a numeric value)
 */
object Operators {

    /**
     * Looks up a known [Condition.Operator] by its [Operator.id].
     *
     * @param id The id of the [Operator]
     * @return The [Operator] identified by the [id]; else null
     */
    fun findById(id: String): Operator? {
        return operators[id]
    }

    /**
     * Checks whether the input and a given target are equal to one another.
     */
    val equals: Operator =
        EqualsOperator("equals", ignoreCase = false, not = false)

    /**
     * Checks whether the input and a given target are equal to one another in a case-insensitive way
     */
    val equalsIgnoreCase: Operator =
        EqualsOperator("equals_ignore_case", ignoreCase = true, not = false)

    /**
     * Checks whether the input and a given target are not equal to one another.
     */
    val doesNotEqual: Operator =
        EqualsOperator("does_not_equal", ignoreCase = false, not = true)

    /**
     * Checks whether the input and a given target are not equal to one another, even in a
     * case-insensitive way
     */
    val doesNotEqualIgnoreCase: Operator =
        EqualsOperator("does_not_equal_ignore_case", ignoreCase = true, not = true)

    /**
     * Checks whether the input starts with a given prefix.
     */
    val startsWith: Operator =
        StringsMatchOperator(
            "starts_with",
            ignoreCase = false,
            not = false,
            comparator = String::startsWith
        )

    /**
     * Checks whether the input starts with a given prefix in a case-insensitive way
     */
    val startsWithIgnoreCase: Operator =
        StringsMatchOperator(
            "starts_with_ignore_case",
            ignoreCase = true,
            not = false,
            comparator = String::startsWith
        )

    /**
     * Checks whether the input does not start with a given prefix.
     */
    val doesNotStartWith: Operator =
        StringsMatchOperator(
            "does_not_start_with",
            ignoreCase = false,
            not = true,
            comparator = String::startsWith
        )

    /**
     * Checks whether the input does not start with a given prefix in a case-insensitive way.
     */
    val doesNotStartWithIgnoreCase: Operator =
        StringsMatchOperator(
            "does_not_start_with_ignore_case",
            ignoreCase = true,
            not = true,
            comparator = String::startsWith
        )

    /**
     * Checks whether the input ends with a given suffix.
     */
    val endsWith: Operator =
        StringsMatchOperator(
            "ends_with",
            ignoreCase = false,
            not = false,
            comparator = String::endsWith
        )

    /**
     * Checks whether the input ends with a given suffix in a case-insensitive way.
     */
    val endsWithIgnoreCase: Operator =
        StringsMatchOperator(
            "ends_with_ignore_case",
            ignoreCase = true,
            not = false,
            comparator = String::endsWith
        )

    /**
     * Checks whether the input does not end with a same given suffix.
     */
    val doesNotEndWith: Operator =
        StringsMatchOperator(
            "does_not_end_with",
            ignoreCase = false,
            not = true,
            comparator = String::endsWith
        )

    /**
     * Checks whether the input does not end with a same given suffix in a case-insensitive way.
     */
    val doesNotEndWithIgnoreCase: Operator =
        StringsMatchOperator(
            "does_not_end_with_ignore_case",
            ignoreCase = true,
            not = true,
            comparator = String::endsWith
        )

    /**
     * Checks whether the input, as a string, contains a given string within it.
     */
    val contains: Operator =
        StringsMatchOperator(
            "contains",
            ignoreCase = false,
            not = false,
            comparator = String::contains
        )

    /**
     * Checks whether the input, as a string, contains a given string within it, in a case-insensitive way.
     */
    val containsIgnoreCase: Operator =
        StringsMatchOperator(
            "contains_ignore_case",
            ignoreCase = true,
            not = false,
            comparator = String::contains
        )

    /**
     * Checks whether the input, as a string, does not contain a given string within it.
     */
    val doesNotContain: Operator =
        StringsMatchOperator(
            "does_not_contain",
            ignoreCase = false,
            not = true,
            comparator = String::contains
        )

    /**
     * Checks whether the input, as a string, does not contain a given string within it, in a
     * case-insensitive way.
     */
    val doesNotContainIgnoreCase: Operator =
        StringsMatchOperator(
            "does_not_contain_ignore_case",
            ignoreCase = true,
            not = true,
            comparator = String::contains
        )

    /**
     * Checks whether a value exists at the given input key in the payload; that is, a request for
     * the given input key does not return `null`.
     */
    val isDefined: Operator = CustomOperator("defined") { item: DataItem?, _: String? ->
        item != null
    }

    /**
     * Checks whether a value does not exist at the given input key in the payload; that is, a
     * request for the given input key returns `null`.
     */
    val isNotDefined: Operator = CustomOperator("notdefined") { item: DataItem?, _: String? ->
        item == null
    }

    /**
     * Checks whether a value exists at the given input key in the payload, and that its value is
     * considered to be "populated".
     *
     * "populated" is considered as the following for the different supported input types:
     *  - [String] != ""
     *  - [DataList.size] != 0
     *  - [DataObject.size] != 0
     *  - `value != null`, `value != [DataItem.NULL]`
     *
     *  Numeric values are always considered as populated.
     */
    val isPopulated: Operator =
        CustomOperator("populated") { item, _ ->
            isDefined.apply(item, null) && item?.isEmpty == false
        }

    /**
     * Checks whether a value exists at the given input key in the payload, and that its value is
     * considered to be "not populated".
     *
     * "not populated" is considered as the following for the different supported input types:
     *  - [String] == ""
     *  - [DataList.size] == 0
     *  - [DataObject.size] == 0
     *  - `value == null`, `value == [DataItem.NULL]`
     *
     *  Numeric values are always considered as populated.
     */
    val isNotPopulated: Operator =
        CustomOperator("notpopulated") { item, _ ->
            isDefined.apply(item, null) && item?.isEmpty == true
        }

    /**
     * Checks whether an input number is greater than a given other number.
     *
     * If the given other number cannot be parsed as a number, then the result will always be `false`
     */
    val greaterThan: Operator =
        NumericOperator("greater_than", greaterThan = true, orEquals = false)

    /**
     * Checks whether an input number is greater than or equals to a given other number.
     *
     * If the given other number cannot be parsed as a number, then the result will always be `false`
     */
    val greaterThanOrEquals: Operator =
        NumericOperator("greater_than_equal_to", greaterThan = true, orEquals = true)

    /**
     * Checks whether an input number is less than a given other number.
     *
     * If the given other number cannot be parsed as a number, then the result will always be `false`
     */
    val lessThan: Operator =
        NumericOperator("less_than", greaterThan = false, orEquals = false)

    /**
     * Checks whether an input number is less than or equals to a given other number.
     *
     * If the given other number cannot be parsed as a number, then the result will always be `false`
     */
    val lessThanOrEquals: Operator =
        NumericOperator("less_than_equal_to", greaterThan = false, orEquals = true)

    /**
     * Checks whether an input is matched by a provided RegularExpression.
     */
    val regularExpression: Operator =
        StringsMatchOperator("regular_expression", false) { string, pattern ->
            Regex(pattern).containsMatchIn(string)
        }

    /**
     * Checks whether a badge for the given value is present.
     */
    val isBadgeAssigned: Operator =
        CustomOperator("is_badge_assigned", isDefined::apply)

    /**
     * Checks whether a badge for the given value is not present.
     */
    val isBadgeNotAssigned: Operator =
        CustomOperator("is_badge_not_assigned", isNotDefined::apply)

    private val operators = listOf(
        equals, equalsIgnoreCase,
        doesNotEqual, doesNotEqualIgnoreCase,
        startsWith, startsWithIgnoreCase,
        doesNotStartWith, doesNotStartWithIgnoreCase,
        endsWith, endsWithIgnoreCase,
        doesNotEndWith, doesNotEndWithIgnoreCase,
        contains, containsIgnoreCase,
        doesNotContain, doesNotContainIgnoreCase,
        isDefined, isNotDefined,
        isPopulated, isNotPopulated,
        greaterThan, greaterThanOrEquals,
        lessThan, lessThanOrEquals,
        regularExpression,
        isBadgeAssigned, isBadgeNotAssigned
    ).associateBy(Operator::id)

    private val DataItem.isEmpty: Boolean
        get() {
            return when (val value = this.value) {
                null, DataItem.NULL -> true
                is DataObject -> value.size == 0
                is DataList -> value.size == 0
                is String -> value.isEmpty()
                else -> false
            }
        }
}

/**
 * A simple [Operator] implementation that uses the [predicate] as the implementation of [apply]
 */
class CustomOperator(
    override val id: String,
    private val predicate: (DataItem?, String?) -> Boolean
) : Operator {
    override fun apply(dataItem: DataItem?, filter: String?): Boolean =
        predicate.invoke(dataItem, filter)
}

/**
 * An [Operator] implementation that performs string comparison using the provided [comparator]
 */
class StringsMatchOperator(
    override val id: String,
    private val ignoreCase: Boolean,
    private val not: Boolean = false,
    private val comparator: (String, String) -> Boolean
) : Operator {

    override fun apply(dataItem: DataItem?, filter: String?): Boolean {
        if (filter == null || dataItem == null)
            return false

        return not xor stringsMatch(dataItem, filter, ignoreCase, comparator)
    }

    private fun stringsMatch(
        dataItem: DataItem,
        filter: String,
        ignoreCase: Boolean,
        predicate: (String, String) -> Boolean
    ): Boolean {
        var target = filter
        var value = dataItem.value.toString()
        if (ignoreCase) {
            value = value.lowercase()
            target = target.lowercase()
        }

        return predicate(value, target)
    }
}

/**
 * An [Operator] that performs an equality comparison on the [DataItem] received.
 *
 * By default the standard [DataItem.equals] method is used for comparison. However, if [ignoreCase]
 * is true, then the values will be coerced to a [String] and lower cased before comparison.
 */
class EqualsOperator(
    override val id: String,
    private val ignoreCase: Boolean,
    private val not: Boolean,
) : Operator {

    override fun apply(dataItem: DataItem?, filter: String?): Boolean {
        if (filter == null || dataItem == null)
            return false

        return not xor isEqual(dataItem, filter, ignoreCase)
    }

    private fun isEqual(
        dataItem: DataItem,
        filter: String,
        ignoreCase: Boolean
    ): Boolean {
        if (ignoreCase) {
            val valueString = dataItem.value.toString()
            return valueString.lowercase() == filter.lowercase()
        }

        return dataItem == DataItem.parse(filter)
    }
}

/**
 * An [Operator] that compares two numeric values.
 *
 * [DataItem] and filter values are coerced to [Double] to compute the comparison.
 *
 * For "greater than" (>) comparisons, set [greaterThan] to `true`, or for "less than" comparisons,
 * set [greaterThan] to `false`
 *
 * If numbers can also be equal then set [orEquals] to `true`; else `false`
 */
class NumericOperator(
    override val id: String,
    private val greaterThan: Boolean,
    private val orEquals: Boolean
) : Operator {

    override fun apply(dataItem: DataItem?, filter: String?): Boolean {
        val number = dataItem?.getDouble()
        if (filter == null || number == null)
            return false

        return compareNumbers(number, filter)
    }

    private fun compareNumbers(
        number: Double,
        filter: String,
    ): Boolean {
        val target = try {
            DataItem.parse(filter)
                .getDouble() ?: return false
        } catch (ex: UnsupportedDataItemException) {
            return false
        }

        return compareNumbers(number, target)
    }

    private fun compareNumbers(double1: Double, double2: Double): Boolean {
        val result = double1.compareTo(double2)
        return if (orEquals && result == 0) {
            true
        } else if (greaterThan) {
            result > 0
        } else {
            result < 0
        }
    }
}
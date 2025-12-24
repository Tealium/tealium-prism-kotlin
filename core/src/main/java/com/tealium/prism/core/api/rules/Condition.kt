package com.tealium.prism.core.api.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.ReferenceContainer
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.key
import com.tealium.prism.core.api.data.ReferenceContainer.Companion.path
import com.tealium.prism.core.api.rules.Condition.Companion.isDefined
import com.tealium.prism.core.api.rules.Condition.Companion.isNotDefined
import com.tealium.prism.core.api.data.ValueContainer
import com.tealium.prism.core.internal.rules.Operators

/**
 * A class modelling the possible conditions used by both transformations and load rules to determine
 * whether or not to execute.
 *
 * If the [variable] to access resides in a sub-object, then use the [path] variable to extract
 * the value from the appropriate location.
 *
 * Although [Condition] instances can be made manually, there are many helper functions to make
 * creations more straightforward and easy to read.
 *
 * With the exception of [isDefined]/[isNotDefined] which explicitly check for the existence of values
 * at given keys, [Operator]s are typically expected to
 *  - throw [MissingDataItemException] if the data item is not found in the payload
 *  - throw [MissingFilterException] if the filter is `null`
 *  - throw [NumberParseException] if it requires a numeric value in the payload or filter, but they were not parseable as such
 *  - throw [UnsupportedOperatorException] if the operator is not supported for the type of data found in the payload
 *
 * @param variable The actual key in the [DataObject] to get the value from
 * @param operator The behavior of this [Condition]
 * @param filter The target value, in String format.
 */
data class Condition internal constructor(
    val variable: ReferenceContainer,
    val operator: Operator,
    val filter: ValueContainer?
) : DataItemConvertible, Matchable<DataObject> {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(Converter.KEY_VARIABLE, variable)
            put(Converter.KEY_OPERATOR, operator)

            filter?.let {
                put(Converter.KEY_FILTER, it)
            }
        }.asDataItem()
    }

    override fun matches(input: DataObject): Boolean {
        val item = input.extract(variable.path)
        return try {
            operator.apply(item, filter)
        } catch (ex: OperatorFailedException) {
            throw ConditionEvaluationException(this, ex)
        }
    }

    /**
     * A [Condition.Operator] is a specific type of predicate, that applies some functionality to
     * a given [DataItem], and determines if it matches a given target filter value.
     */
    interface Operator : DataItemConvertible {

        /**
         * The id of this [Operator] for the purposes of reading to/from a [DataItem]/[DataObject]
         */
        val id: String

        /**
         * Called to determine if the given [dataItem] matches the [filter] for this [Operator].
         *
         * If the [dataItem] is null, then the variable was not available in the payload.
         *
         * @param dataItem The [DataItem] to apply the operator to.
         * @param filter The target value to check if the [dataItem] matches
         *
         * @return true if the [dataItem] matches the target [filter] for this [Operator]
         * @throws OperatorFailedException when the [Operator] cannot be evaluated successfully.
         */
        @Throws(OperatorFailedException::class)
        fun apply(dataItem: DataItem?, filter: ValueContainer?): Boolean

        override fun asDataItem(): DataItem {
            return DataItem.string(id)
        }

        // todo - could be moved to internal
        object Converter : DataItemConverter<Operator> {
            override fun convert(dataItem: DataItem): Operator? {
                val operatorString = dataItem.getString() ?: return null

                return Operators.findById(operatorString)
            }
        }
    }

    // todo - could be moved internal?
    object Converter : DataItemConverter<Condition> {
        const val KEY_VARIABLE = "variable"
        const val KEY_OPERATOR = "operator"
        const val KEY_FILTER = "filter"

        override fun convert(dataItem: DataItem): Condition? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val variable = dataObject.get(KEY_VARIABLE, ReferenceContainer.Converter)
            val operator = dataObject.get(KEY_OPERATOR, Operator.Converter)
            if (variable == null || operator == null)
                return null

            val filter = dataObject.get(KEY_FILTER, ValueContainer.Converter)

            return Condition(variable, operator, filter)
        }
    }

    companion object {

        /**
         * Returns an [Operator] that checks whether the value found at key [variable] is equal to
         * the given [target].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the key to extract the value from for the comparison
         * @param target the target value to check against
         */
        @JvmStatic
        fun isEqual(ignoreCase: Boolean, variable: String, target: String): Condition =
            isEqual(ignoreCase, key(variable), target)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable] is equal to
         * the given [target].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param target the target value to check against
         */
        @JvmStatic
        fun isEqual(ignoreCase: Boolean, variable: JsonObjectPath, target: String): Condition =
            isEqual(ignoreCase, path(variable), target)

        private fun isEqual(
            ignoreCase: Boolean,
            variable: ReferenceContainer,
            target: String
        ): Condition =
            Condition(
                variable,
                if (ignoreCase) Operators.equalsIgnoreCase else Operators.equals,
                ValueContainer(target)
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable] is not equal
         * to the given [target].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the key to extract the value from for the comparison
         * @param target the target value to check against
         */
        @JvmStatic
        fun doesNotEqual(ignoreCase: Boolean, variable: String, target: String): Condition =
            doesNotEqual(ignoreCase, key(variable), target)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable] is not equal
         * to the given [target].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param target the target value to check against
         */
        @JvmStatic
        fun doesNotEqual(ignoreCase: Boolean, variable: JsonObjectPath, target: String): Condition =
            doesNotEqual(ignoreCase, path(variable), target)

        private fun doesNotEqual(
            ignoreCase: Boolean,
            variable: ReferenceContainer,
            target: String
        ): Condition =
            Condition(
                variable,
                if (ignoreCase) Operators.doesNotEqualIgnoreCase else Operators.doesNotEqual,
                ValueContainer(target)
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * contains the [string] within it.
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the key to extract the value from for the comparison
         * @param string the target value to check against
         */
        @JvmStatic
        fun contains(ignoreCase: Boolean, variable: String, string: String): Condition =
            contains(ignoreCase, key(variable), string)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * contains the [string] within it.
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param string the target value to check against
         */
        @JvmStatic
        fun contains(ignoreCase: Boolean, variable: JsonObjectPath, string: String): Condition =
            contains(ignoreCase, path(variable), string)

        private fun contains(
            ignoreCase: Boolean,
            variable: ReferenceContainer,
            string: String
        ): Condition =
            Condition(
                variable,
                if (ignoreCase) Operators.containsIgnoreCase else Operators.contains,
                ValueContainer(string)
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not contain the [string] within it.
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the key to extract the value from for the comparison
         * @param string the target value to check against
         */
        @JvmStatic
        fun doesNotContain(ignoreCase: Boolean, variable: String, string: String): Condition =
            doesNotContain(ignoreCase, key(variable), string)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not contain the [string] within it.
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param string the target value to check against
         */
        @JvmStatic
        fun doesNotContain(
            ignoreCase: Boolean,
            variable: JsonObjectPath,
            string: String
        ): Condition =
            doesNotContain(ignoreCase, path(variable), string)

        private fun doesNotContain(
            ignoreCase: Boolean,
            variable: ReferenceContainer,
            string: String
        ): Condition =
            Condition(
                variable,
                if (ignoreCase) Operators.doesNotContainIgnoreCase else Operators.doesNotContain,
                ValueContainer(string)
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * starts with the given [prefix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the key to extract the value from for the comparison
         * @param prefix the target value to check against
         */
        @JvmStatic
        fun startsWith(ignoreCase: Boolean, variable: String, prefix: String): Condition =
            startsWith(ignoreCase, key(variable), prefix)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * starts with the given [prefix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param prefix the target value to check against
         */
        @JvmStatic
        fun startsWith(ignoreCase: Boolean, variable: JsonObjectPath, prefix: String): Condition =
            startsWith(ignoreCase, path(variable), prefix)

        private fun startsWith(
            ignoreCase: Boolean,
            variable: ReferenceContainer,
            prefix: String
        ): Condition =
            Condition(
                variable,
                if (ignoreCase) Operators.startsWithIgnoreCase else Operators.startsWith,
                ValueContainer(prefix)
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not start with the given [prefix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the key to extract the value from for the comparison
         * @param prefix the target value to check against
         */
        @JvmStatic
        fun doesNotStartWith(ignoreCase: Boolean, variable: String, prefix: String): Condition =
            doesNotStartWith(ignoreCase, key(variable), prefix)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not start with the given [prefix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param prefix the target value to check against
         */
        @JvmStatic
        fun doesNotStartWith(
            ignoreCase: Boolean,
            variable: JsonObjectPath,
            prefix: String
        ): Condition =
            doesNotStartWith(ignoreCase, path(variable), prefix)

        private fun doesNotStartWith(
            ignoreCase: Boolean,
            variable: ReferenceContainer,
            prefix: String
        ): Condition =
            Condition(
                variable,
                if (ignoreCase) Operators.doesNotStartWithIgnoreCase else Operators.doesNotStartWith,
                ValueContainer(prefix)
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * ends with the given [suffix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the key to extract the value from for the comparison
         * @param suffix the target value to check against
         */
        @JvmStatic
        fun endsWith(ignoreCase: Boolean, variable: String, suffix: String): Condition =
            endsWith(ignoreCase, key(variable), suffix)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * ends with the given [suffix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param suffix the target value to check against
         */
        @JvmStatic
        fun endsWith(ignoreCase: Boolean, variable: JsonObjectPath, suffix: String): Condition =
            endsWith(ignoreCase, path(variable), suffix)

        private fun endsWith(
            ignoreCase: Boolean,
            variable: ReferenceContainer,
            suffix: String
        ): Condition =
            Condition(
                variable,
                if (ignoreCase) Operators.endsWithIgnoreCase else Operators.endsWith,
                ValueContainer(suffix)
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not end with the given [suffix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the key to extract the value from for the comparison
         * @param suffix the target value to check against
         */
        @JvmStatic
        fun doesNotEndWith(ignoreCase: Boolean, variable: String, suffix: String): Condition =
            doesNotEndWith(ignoreCase, key(variable), suffix)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not end with the given [suffix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param suffix the target value to check against
         */
        @JvmStatic
        fun doesNotEndWith(
            ignoreCase: Boolean,
            variable: JsonObjectPath,
            suffix: String
        ): Condition =
            doesNotEndWith(ignoreCase, path(variable), suffix)

        private fun doesNotEndWith(
            ignoreCase: Boolean,
            variable: ReferenceContainer,
            suffix: String
        ): Condition =
            Condition(
                variable,
                if (ignoreCase) Operators.doesNotEndWithIgnoreCase else Operators.doesNotEndWith,
                ValueContainer(suffix)
            )

        /**
         * Returns an [Operator] that checks whether a value can be found at key [variable].
         *
         * @param variable the key to extract the value from for the comparison
         */
        @JvmStatic
        fun isDefined(variable: String): Condition =
            isDefined(key(variable))

        /**
         * Returns an [Operator] that checks whether a value can be found at key [variable].
         *
         * @param variable the path to the key to extract the value from for the comparison
         */
        @JvmStatic
        fun isDefined(variable: JsonObjectPath): Condition =
            isDefined(path(variable))

        private fun isDefined(variable: ReferenceContainer): Condition =
            Condition(variable, Operators.isDefined, null)

        /**
         * Returns an [Operator] that checks whether a value can not be found at key [variable].
         *
         * @param variable the key to extract the value from for the comparison
         */
        @JvmStatic
        fun isNotDefined(variable: String): Condition =
            isNotDefined(key(variable))

        /**
         * Returns an [Operator] that checks whether a value can not be found at key [variable].
         *
         * @param variable the path to the key to extract the value from for the comparison
         */
        @JvmStatic
        fun isNotDefined(variable: JsonObjectPath): Condition =
            isNotDefined(path(variable))

        private fun isNotDefined(variable: ReferenceContainer): Condition =
            Condition(variable, Operators.isNotDefined, null)

        /**
         * Returns an [Operator] that checks whether a value found at key [variable] would be considered
         * "not empty"
         *
         * "not empty" is considered as the following for the different supported input types:
         *  - [String] != ""
         *  - [DataList.size] != 0
         *  - [DataObject.size] != 0
         *  - `value != [DataItem.NULL]`
         *
         * Numeric values are always considered as not empty.
         *
         * @param variable the key to extract the value from for the comparison
         */
        @JvmStatic
        fun isNotEmpty(variable: String): Condition =
            isNotEmpty(key(variable))

        /**
         * Returns an [Operator] that checks whether a value found at key [variable] would be considered
         * "not empty"
         *
         * "not empty" is considered as the following for the different supported input types:
         *  - [String] != ""
         *  - [DataList.size] != 0
         *  - [DataObject.size] != 0
         *  - `value != [DataItem.NULL]`
         *
         * Numeric values are always considered as not empty.
         *
         * @param variable the path to the key to extract the value from for the comparison
         */
        @JvmStatic
        fun isNotEmpty(variable: JsonObjectPath): Condition =
            isNotEmpty(path(variable))

        private fun isNotEmpty(variable: ReferenceContainer): Condition =
            Condition(variable, Operators.isNotEmpty, null)

        /**
         * Returns an [Operator] that checks whether a value found at key [variable] would be considered
         * "empty"
         *
         * "empty" is considered as the following for the different supported input types:
         *  - [String] == ""
         *  - [DataList.size] == 0
         *  - [DataObject.size] == 0
         *  - `value == [DataItem.NULL]`
         *
         * Numeric values are always considered as "not empty".
         *
         * @param variable the key to extract the value from for the comparison
         */
        @JvmStatic
        fun isEmpty(variable: String): Condition =
            isEmpty(key(variable))

        /**
         * Returns an [Operator] that checks whether a value found at key [variable] would be considered
         * "empty"
         *
         * "empty" is considered as the following for the different supported input types:
         *  - [String] == ""
         *  - [DataList.size] == 0
         *  - [DataObject.size] == 0
         *  - `value == [DataItem.NULL]`
         *
         * Numeric values are always considered as "not empty".
         *
         * @param variable the path to the key to extract the value from for the comparison
         */
        @JvmStatic
        fun isEmpty(variable: JsonObjectPath): Condition =
            isEmpty(path(variable))

        private fun isEmpty(variable: ReferenceContainer): Condition =
            Condition(variable, Operators.isEmpty, null)

        /**
         * Returns an [Operator] that checks whether the numeric value found at key [variable], is
         * greater than the numeric value given by [number].
         *
         * @param orEqual `true` if numbers can also be equal; else false
         * @param variable the key to extract the value from for the comparison
         * @param number the target value to check against
         */
        @JvmStatic
        fun isGreaterThan(orEqual: Boolean, variable: String, number: String): Condition =
            isGreaterThan(orEqual, key(variable), number)

        /**
         * Returns an [Operator] that checks whether the numeric value found at key [variable], is
         * greater than the numeric value given by [number].
         *
         * @param orEqual `true` if numbers can also be equal; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param number the target value to check against
         */
        @JvmStatic
        fun isGreaterThan(orEqual: Boolean, variable: JsonObjectPath, number: String): Condition =
            isGreaterThan(orEqual, path(variable), number)

        private fun isGreaterThan(
            orEqual: Boolean,
            variable: ReferenceContainer,
            number: String
        ): Condition =
            Condition(
                variable,
                if (orEqual) Operators.greaterThanOrEquals else Operators.greaterThan,
                ValueContainer(number)
            )

        /**
         * Returns an [Operator] that checks whether the numeric value found at key [variable], is
         * less than the numeric value given by [number].
         *
         * @param orEqual `true` if numbers can also be equal; else false
         * @param variable the key to extract the value from for the comparison
         * @param number the target value to check against
         */
        @JvmStatic
        fun isLessThan(orEqual: Boolean, variable: String, number: String): Condition =
            isLessThan(orEqual, key(variable), number)

        /**
         * Returns an [Operator] that checks whether the numeric value found at key [variable], is
         * less than the numeric value given by [number].
         *
         * @param orEqual `true` if numbers can also be equal; else false
         * @param variable the path to the key to extract the value from for the comparison
         * @param number the target value to check against
         */
        @JvmStatic
        fun isLessThan(orEqual: Boolean, variable: JsonObjectPath, number: String): Condition =
            isLessThan(orEqual, path(variable), number)

        private fun isLessThan(
            orEqual: Boolean,
            variable: ReferenceContainer,
            number: String
        ): Condition =
            Condition(
                variable,
                if (orEqual) Operators.lessThanOrEquals else Operators.lessThan,
                ValueContainer(number)
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], is matched
         * by the given [regex] string.
         *
         * @param variable the key to extract the value from for the comparison
         * @param regex the target regex to check against
         */
        @JvmStatic
        fun regularExpression(variable: String, regex: String): Condition =
            regularExpression(key(variable), regex)

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], is matched
         * by the given [regex] string.
         *
         * @param variable the path to the key to extract the value from for the comparison
         * @param regex the target regex to check against
         */
        @JvmStatic
        fun regularExpression(variable: JsonObjectPath, regex: String): Condition =
            regularExpression(path(variable), regex)

        private fun regularExpression(variable: ReferenceContainer, regex: String): Condition =
            Condition(variable, Operators.regularExpression, ValueContainer(regex))
    }
}
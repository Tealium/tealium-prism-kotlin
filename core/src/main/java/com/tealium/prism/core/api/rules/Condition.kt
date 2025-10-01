package com.tealium.prism.core.api.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.settings.VariableAccessor
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
 * @param path An optional list of consecutive sub-keys for accessing the variable to be checked
 * @param variable The actual key in the [DataObject] to get the value from
 * @param operator The behavior of this [Condition]
 * @param filter The target value, in String format.
 */
data class Condition internal constructor(
    val path: List<String>? = null,
    val variable: String,
    val operator: Operator,
    val filter: String?
) : DataItemConvertible, Matchable<DataObject> {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(Converter.KEY_VARIABLE, variable)
            put(Converter.KEY_OPERATOR, operator)

            path?.let {
                put(Converter.KEY_PATH, it.asDataList())
            }
            filter?.let {
                put(Converter.KEY_FILTER, it)
            }
        }.asDataItem()
    }

    override fun matches(input: DataObject): Boolean {
        val variableAccessor = VariableAccessor(variable, path)
        val item = input.extract(variableAccessor)
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
        fun apply(dataItem: DataItem?, filter: String?): Boolean

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
        const val KEY_PATH = "path"
        const val KEY_VARIABLE = "variable"
        const val KEY_OPERATOR = "operator"
        const val KEY_FILTER = "filter"

        override fun convert(dataItem: DataItem): Condition? {
            val dataObject = dataItem.getDataObject()
                ?: return null

            val variable = dataObject.getString(KEY_VARIABLE)
            val operator = dataObject.get(KEY_OPERATOR, Operator.Converter)
            if (variable == null || operator == null)
                return null

            val path = dataObject.getDataList(KEY_PATH)
                ?.mapNotNull { it.getString() }
            val filter = dataObject.getString(KEY_FILTER)

            return Condition(path, variable, operator, filter)
        }
    }

    companion object {

        /**
         * Returns an [Operator] that checks whether the value found at key [variable] is equal to
         * the given [target].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param target the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun isEqual(
            ignoreCase: Boolean,
            path: List<String>? = null,
            variable: String,
            target: String
        ): Condition =
            Condition(
                path,
                variable,
                if (ignoreCase) Operators.equalsIgnoreCase else Operators.equals,
                target
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable] is not equal
         * to the given [target].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param target the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun doesNotEqual(
            ignoreCase: Boolean,
            path: List<String>? = null,
            variable: String,
            target: String
        ): Condition =
            Condition(
                path,
                variable,
                if (ignoreCase) Operators.doesNotEqualIgnoreCase else Operators.doesNotEqual,
                target
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * contains the [string] within it.
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param string the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun contains(
            ignoreCase: Boolean,
            path: List<String>? = null,
            variable: String,
            string: String
        ): Condition =
            Condition(
                path,
                variable,
                if (ignoreCase) Operators.containsIgnoreCase else Operators.contains,
                string
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not contain the [string] within it.
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param string the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun doesNotContain(
            ignoreCase: Boolean,
            path: List<String>? = null,
            variable: String,
            string: String
        ): Condition =
            Condition(
                path,
                variable,
                if (ignoreCase) Operators.doesNotContainIgnoreCase else Operators.doesNotContain,
                string
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * starts with the given [prefix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param prefix the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun startsWith(
            ignoreCase: Boolean,
            path: List<String>? = null,
            variable: String,
            prefix: String
        ): Condition =
            Condition(
                path,
                variable,
                if (ignoreCase) Operators.startsWithIgnoreCase else Operators.startsWith,
                prefix
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not start with the given [prefix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param prefix the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun doesNotStartWith(
            ignoreCase: Boolean,
            path: List<String>? = null,
            variable: String,
            prefix: String
        ): Condition =
            Condition(
                path,
                variable,
                if (ignoreCase) Operators.doesNotStartWithIgnoreCase else Operators.doesNotStartWith,
                prefix
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * ends with the given [suffix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param suffix the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun endsWith(
            ignoreCase: Boolean,
            path: List<String>? = null,
            variable: String,
            suffix: String
        ): Condition =
            Condition(
                path,
                variable,
                if (ignoreCase) Operators.endsWithIgnoreCase else Operators.endsWith,
                suffix
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], as a string,
         * does not end with the given [suffix].
         *
         * @param ignoreCase `true` if the comparison should be done in a case-insensitive way; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param suffix the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun doesNotEndWith(
            ignoreCase: Boolean,
            path: List<String>? = null,
            variable: String,
            suffix: String
        ): Condition =
            Condition(
                path,
                variable,
                if (ignoreCase) Operators.doesNotEndWithIgnoreCase else Operators.doesNotEndWith,
                suffix
            )

        /**
         * Returns an [Operator] that checks whether a value can be found at key [variable].
         *
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         */
        @JvmStatic
        @JvmOverloads
        fun isDefined(
            path: List<String>? = null,
            variable: String,
        ): Condition =
            Condition(
                path,
                variable,
                Operators.isDefined,
                null
            )

        /**
         * Returns an [Operator] that checks whether a value can not be found at key [variable].
         *
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         */
        @JvmStatic
        @JvmOverloads
        fun isNotDefined(
            path: List<String>? = null,
            variable: String,
        ): Condition =
            Condition(
                path,
                variable,
                Operators.isNotDefined,
                null
            )

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
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         */
        @JvmStatic
        @JvmOverloads
        fun isNotEmpty(
            path: List<String>? = null,
            variable: String,
        ): Condition =
            Condition(
                path,
                variable,
                Operators.isNotEmpty,
                null
            )

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
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         */
        @JvmStatic
        @JvmOverloads
        fun isEmpty(
            path: List<String>? = null,
            variable: String,
        ): Condition =
            Condition(
                path,
                variable,
                Operators.isEmpty,
                null
            )

        /**
         * Returns an [Operator] that checks whether the numeric value found at key [variable], is
         * greater than the numeric value given by [number].
         *
         * @param orEqual `true` if numbers can also be equal; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param number the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun isGreaterThan(
            orEqual: Boolean,
            path: List<String>? = null,
            variable: String,
            number: String
        ): Condition =
            Condition(
                path,
                variable,
                if (orEqual) Operators.greaterThanOrEquals else Operators.greaterThan,
                number
            )

        /**
         * Returns an [Operator] that checks whether the numeric value found at key [variable], is
         * less than the numeric value given by [number].
         *
         * @param orEqual `true` if numbers can also be equal; else false
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param number the target value to check against
         */
        @JvmStatic
        @JvmOverloads
        fun isLessThan(
            orEqual: Boolean,
            path: List<String>? = null,
            variable: String,
            number: String
        ): Condition =
            Condition(
                path,
                variable,
                if (orEqual) Operators.lessThanOrEquals else Operators.lessThan,
                number
            )

        /**
         * Returns an [Operator] that checks whether the value found at key [variable], is matched
         * by the given [regex] string.
         *
         * @param path optional list of keys that form the access to sub-objects when accessing the [variable]
         * @param variable the key to extract the value from for the comparison
         * @param regex the target regex to check against
         */
        @JvmStatic
        @JvmOverloads
        fun regularExpression(
            path: List<String>? = null,
            variable: String,
            regex: String
        ): Condition =
            Condition(
                path,
                variable,
                Operators.regularExpression,
                regex
            )
    }
}
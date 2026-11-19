@file:JvmName("FailableExtractionUtils")

package com.tealium.prism.core.api.data

// These helpers read values from a `DataObject` and throw `ExtractionException` on failure. A command
// created with `Command.synchronous` can call them directly: when the SDK runs it, a thrown
// `ExtractionException` is reported as `CommandException.invalidPayload`.

// Section: extract. Never throws: an absent or wrong-typed value is `null`.

/**
 * Extracts a [T] from this [DataObject] at the given [path] using the [converter].
 *
 * This never throws: `null` is returned if there is no value or it cannot be converted to [T]. Use
 * the `optional` or `require` functions to be told about a wrong-typed or absent value.
 *
 * @param path The path of the value to extract.
 * @param converter The converter used to create [T] from the extracted [DataItem].
 *
 * @return The retrieved and converted value; or `null` if absent or not convertible.
 */
fun <T> DataObject.extract(path: JsonObjectPathConvertible, converter: DataItemConverter<T>): T? =
    extract(path.asJsonObjectPath(), converter)

/**
 * Extracts a [DataObject] from this [DataObject] at the given [path].
 *
 * This never throws: `null` is returned if there is no value or it is not a [DataObject]. Use the
 * `optionalDataObject` or `requireDataObject` functions to be told about a wrong-typed or absent
 * value.
 *
 * @param path The path of the value to extract.
 *
 * @return The retrieved [DataObject]; or `null` if absent or not a [DataObject].
 */
fun DataObject.extractDataObject(path: JsonObjectPathConvertible): DataObject? =
    extractDataObject(path.asJsonObjectPath())

// Section: optional. An absent value is `null`; a wrong-typed value throws.

/**
 * Extracts a value from this [DataObject] at the given [path] using the [converter] to convert it
 * to [T], or returns `null` if there is no value.
 *
 * An explicit JSON `null` is treated the same as an absent value.
 *
 * The expected type reported in the error message is the simple name of [T], e.g. `String`,
 * taken from `T::class.simpleName`. For types that an app's R8 may rename, use the overload with
 * an explicit `expectedType`.
 *
 * If the value does exist at the given [path] but cannot be converted to [T] then
 * [ExtractionException.TypeMismatch] is thrown.
 *
 * @param path The path of the value to extract.
 * @param converter The converter used to create [T] from the extracted [DataItem].
 *
 * @return The retrieved and converted value; or `null` if absent or an explicit `null`.
 */
@Throws(ExtractionException::class)
inline fun <reified T : Any> DataObject.optional(
    path: JsonObjectPathConvertible,
    converter: DataItemConverter<T>
): T? =
    optional(path, converter, T::class.simpleName ?: T::class.java.name)

/**
 * Extracts a value from this [DataObject] at the given [path] using the [converter] to convert it
 * to [T], or returns `null` if there is no value.
 *
 * This is the entry point for Java callers. Kotlin callers should use the overload that infers
 * the expected type from [T].
 *
 * An explicit JSON `null` is treated the same as an absent value.
 *
 * If the value does exist at the given [path] but cannot be converted to [T] then
 * [ExtractionException.TypeMismatch] is thrown.
 *
 * @param path The path of the value to extract.
 * @param converter The converter used to create [T] from the extracted [DataItem].
 * @param expectedType A human readable description of [T], used in the error message when the
 * conversion fails.
 *
 * @return The retrieved and converted value; or `null` if absent or an explicit `null`.
 */
@Throws(ExtractionException::class)
fun <T> DataObject.optional(
    path: JsonObjectPathConvertible,
    converter: DataItemConverter<T>,
    expectedType: String
): T? {
    val jsonPath = path.asJsonObjectPath()

    val item = extract(jsonPath)
    if (item == null || item.isNull())
        return null

    return converter.convert(item)
        ?: throw ExtractionException.TypeMismatch(jsonPath.toString(), expectedType)
}

/**
 * Extracts a [DataObject] from this [DataObject] at the given [path], or returns `null` if there
 * is no value.
 *
 * An explicit JSON `null` is treated the same as an absent value.
 *
 * If the value does exist at the given [path] but is not a [DataObject] then
 * [ExtractionException.TypeMismatch] is thrown.
 *
 * @param path The path of the value to extract.
 *
 * @return The retrieved [DataObject]; or `null` if absent or an explicit `null`.
 */
@Throws(ExtractionException::class)
fun DataObject.optionalDataObject(path: JsonObjectPathConvertible): DataObject? =
    optional(path, DataItem::getDataObject, "DataObject")

/**
 * Extracts a [String] from this [DataObject] at the given [path] and maps it to one of the given
 * [values], or returns `null` if there is no usable text.
 *
 * The text is read with [LenientConverters.STRING], trimmed, and matched against the keys of
 * [values] ignoring case.
 *
 * An absent value, an explicit JSON `null` and a blank text all return `null`.
 *
 * If the value cannot be converted to a [String] then [ExtractionException.TypeMismatch] is thrown.
 *
 * If the text matches none of the keys then [ExtractionException.InvalidValue] is thrown, listing the
 * accepted keys sorted alphabetically, ignoring case.
 *
 * Keys that differ only by case are ambiguous: the first one in the iteration order of [values] wins.
 *
 * @param path The path of the value to extract.
 * @param values The accepted texts, mapped to the value to return for each.
 *
 * @return The value mapped to the matching key; or `null` if absent, an explicit `null` or blank.
 */
@Throws(ExtractionException::class)
fun <T> DataObject.optionalOneOf(
    path: JsonObjectPathConvertible,
    values: Map<String, T>
): T? {
    val text = optional(path, LenientConverters.STRING)?.trim()
    if (text.isNullOrEmpty())
        return null

    return values.matching(path, text)
}

/**
 * Same as [optionalOneOf] with a [JsonObjectPathConvertible], but reading from a raw [JsonObjectPath].
 *
 * This is useful for a dynamic child path, such as `base.asJsonObjectPath().key(name)` built from a
 * payload key.
 *
 * @param path The path of the value to extract.
 * @param values The accepted texts, mapped to the value to return for each.
 *
 * @return The value mapped to the matching key; or `null` if absent, an explicit `null` or blank.
 */
@Throws(ExtractionException::class)
fun <T> DataObject.optionalOneOf(
    path: JsonObjectPath,
    values: Map<String, T>
): T? =
    optionalOneOf(RawPath(path), values)

// Section: require. An absent or wrong-typed value throws.

/**
 * Extracts the [DataItem] from this [DataObject] at the given [path].
 *
 * If no value exists at the given [path], then [ExtractionException.Missing] is thrown.
 *
 * An explicit JSON `null` is a value: the returned [DataItem] is [DataItem.NULL] and no exception
 * is thrown, so callers can distinguish "set to null" from "absent". The `require` functions that
 * convert the value treat an explicit `null` as missing instead.
 *
 * @param path The path of the value to extract.
 *
 * @return The retrieved [DataItem], which is [DataItem.NULL] for an explicit JSON `null`.
 */
@Throws(ExtractionException::class)
fun DataObject.requireDataItem(path: JsonObjectPathConvertible): DataItem {
    val jsonPath = path.asJsonObjectPath()

    return extract(jsonPath)
        ?: throw ExtractionException.Missing(jsonPath.toString())
}

/**
 * Extracts a value from this [DataObject] at the given [path] using the [converter] to convert it
 * to [T].
 *
 * The expected type reported in the error message is the simple name of [T], e.g. `String`,
 * taken from `T::class.simpleName`. For types that an app's R8 may rename, use the overload with
 * an explicit `expectedType`.
 *
 * If no value exists at the given [path], or it is an explicit JSON `null`, then
 * [ExtractionException.Missing] is thrown.
 *
 * If the value does exist at the given [path] but cannot be converted to [T] then
 * [ExtractionException.TypeMismatch] is thrown.
 *
 * @param path The path of the value to extract.
 * @param converter The converter used to create [T] from the extracted [DataItem].
 *
 * @return The retrieved and converted value.
 */
@Throws(ExtractionException::class)
inline fun <reified T : Any> DataObject.require(
    path: JsonObjectPathConvertible,
    converter: DataItemConverter<T>
): T =
    require(path, converter, T::class.simpleName ?: T::class.java.name)

/**
 * Extracts a value from this [DataObject] at the given [path] using the [converter] to convert it
 * to [T].
 *
 * This is the entry point for Java callers. Kotlin callers should use the overload that infers
 * the expected type from [T].
 *
 * If no value exists at the given [path], or it is an explicit JSON `null`, then
 * [ExtractionException.Missing] is thrown.
 *
 * If the value does exist at the given [path] but cannot be converted to [T] then
 * [ExtractionException.TypeMismatch] is thrown.
 *
 * @param path The path of the value to extract.
 * @param converter The converter used to create [T] from the extracted [DataItem].
 * @param expectedType A human readable description of [T], used in the error message when the
 * conversion fails.
 *
 * @return The retrieved and converted value.
 */
@Throws(ExtractionException::class)
fun <T> DataObject.require(
    path: JsonObjectPathConvertible,
    converter: DataItemConverter<T>,
    expectedType: String
): T {
    val jsonPath = path.asJsonObjectPath()

    val item = extract(jsonPath)
    if (item == null || item.isNull())
        throw ExtractionException.Missing(jsonPath.toString())

    return converter.convert(item)
        ?: throw ExtractionException.TypeMismatch(jsonPath.toString(), expectedType)
}

/**
 * Extracts a [DataObject] from this [DataObject] at the given [path].
 *
 * If no value exists at the given [path], or it is an explicit JSON `null`, then
 * [ExtractionException.Missing] is thrown.
 *
 * If the value does exist at the given [path] but is not a [DataObject] then
 * [ExtractionException.TypeMismatch] is thrown.
 *
 * @param path The path of the value to extract.
 *
 * @return The retrieved [DataObject].
 */
@Throws(ExtractionException::class)
fun DataObject.requireDataObject(path: JsonObjectPathConvertible): DataObject =
    require(path, DataItem::getDataObject, "DataObject")

/**
 * Extracts a [String] value from this [DataObject] at the given [path] using the [converter], and
 * requires it to not be blank.
 *
 * The value is returned unchanged, it is only checked for blankness and not trimmed.
 *
 * If no value exists at the given [path], or it is an explicit JSON `null`, then
 * [ExtractionException.Missing] is thrown.
 *
 * If the value cannot be converted to a [String] then [ExtractionException.TypeMismatch] is thrown.
 *
 * If the value is empty or only whitespace then [ExtractionException.Empty] is thrown.
 *
 * @param path The path of the value to extract.
 * @param converter The converter used to create a [String] from the extracted [DataItem].
 *
 * @return The retrieved and converted non blank [String].
 */
@Throws(ExtractionException::class)
fun DataObject.requireNonBlank(
    path: JsonObjectPathConvertible,
    converter: DataItemConverter<String>
): String {
    val value = require(path, converter)
    if (value.isBlank())
        throw ExtractionException.Empty(path.asJsonObjectPath().toString())

    return value
}

/**
 * Extracts a [String] from this [DataObject] at the given [path] and maps it to one of the given
 * [values].
 *
 * The text is read with [LenientConverters.STRING], trimmed, and matched against the keys of
 * [values] ignoring case.
 *
 * If no value exists at the given [path], or it is an explicit JSON `null`, then
 * [ExtractionException.Missing] is thrown.
 *
 * If the value cannot be converted to a [String] then [ExtractionException.TypeMismatch] is thrown.
 *
 * If the text is blank then [ExtractionException.Empty] is thrown.
 *
 * If the text matches none of the keys then [ExtractionException.InvalidValue] is thrown, listing the
 * accepted keys sorted alphabetically, ignoring case.
 *
 * Keys that differ only by case are ambiguous: the first one in the iteration order of [values] wins.
 *
 * @param path The path of the value to extract.
 * @param values The accepted texts, mapped to the value to return for each.
 *
 * @return The value mapped to the matching key.
 */
@Throws(ExtractionException::class)
fun <T> DataObject.requireOneOf(
    path: JsonObjectPathConvertible,
    values: Map<String, T>
): T {
    val text = require(path, LenientConverters.STRING).trim()
    if (text.isEmpty())
        throw ExtractionException.Empty(path.asJsonObjectPath().toString())

    return values.matching(path, text)
}

/**
 * Same as [requireOneOf] with a [JsonObjectPathConvertible], but reading from a raw [JsonObjectPath].
 *
 * This is useful for a dynamic child path, such as `base.asJsonObjectPath().key(name)` built from a
 * payload key.
 *
 * @param path The path of the value to extract.
 * @param values The accepted texts, mapped to the value to return for each.
 *
 * @return The value mapped to the matching key.
 */
@Throws(ExtractionException::class)
fun <T> DataObject.requireOneOf(
    path: JsonObjectPath,
    values: Map<String, T>
): T =
    requireOneOf(RawPath(path), values)

private fun <T> Map<String, T>.matching(path: JsonObjectPathConvertible, text: String): T {
    val entry = entries.firstOrNull { it.key.equals(text, ignoreCase = true) }
        ?: throw ExtractionException.InvalidValue(
            path.asJsonObjectPath().toString(),
            "expected one of ${keys.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString(", ")}"
        )

    return entry.value
}

// Lets the raw path overloads share the implementation of the JsonObjectPathConvertible ones.
private class RawPath(private val path: JsonObjectPath) : JsonObjectPathConvertible {
    override fun asJsonObjectPath(): JsonObjectPath = path
}

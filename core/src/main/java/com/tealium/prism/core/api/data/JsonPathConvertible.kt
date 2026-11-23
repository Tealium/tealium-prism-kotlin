package com.tealium.prism.core.api.data

/**
 * Implemented by types that can be converted to a [JsonPath] whose first component is a [TRoot].
 *
 * [JsonPath] implements it by returning itself, so a path built at runtime can be passed wherever
 * a [JsonPathConvertible] with the same root is expected. For example, the [DataObject]
 * extraction helpers such as `require` and `optional` accept a dynamic child path like
 * `base.asJsonObjectPath().key(name)`, built from a payload key.
 */
interface JsonPathConvertible<TRoot : JsonPath.Component> {

    /**
     * Returns the [JsonPath] representation of this type.
     */
    fun asJsonPath(): JsonPath<TRoot>
}

package com.tealium.prism.core.api.data

import com.tealium.prism.core.internal.data.ReferenceType

/**
 * A container for a reference to a variable in a [DataObject]
 */
class ReferenceContainer private constructor(
    private val referenceType: ReferenceType
): DataObjectConvertible by referenceType {

    /**
     * Returns the [JsonObjectPath] to use to retrieve the reference from a [DataObject]
     */
    val path: JsonObjectPath
        get() = referenceType.asJsonPath()

    companion object {

        /**
         * Creates a [ReferenceContainer] where the reference is to be found in the top level of a
         * [DataObject] under the given [key].
         */
        @JvmStatic
        fun key(key: String) : ReferenceContainer =
            ReferenceContainer(ReferenceType.Key(key))

        /**
         * Creates a [ReferenceContainer] where the variable is to be found in a [DataObject], possibly
         * nested under other [DataObject]s or [DataList]s, according to the given [path].
         */
        @JvmStatic
        fun path(path: JsonObjectPath) : ReferenceContainer =
            ReferenceContainer(ReferenceType.Path(path))
    }

    object Converter: DataItemConverter<ReferenceContainer> {
        const val KEY_KEY = "key"
        const val KEY_PATH = "path"

        override fun convert(dataItem: DataItem): ReferenceContainer? {
            val dataObject = dataItem.getDataObject() ?: return null

            val path = dataObject.get(KEY_PATH, JsonPath.Converter)
            if (path != null) {
                runCatching {
                    return ReferenceContainer(ReferenceType.Path(path.requireObjectPath()))
                }
            }

            val key = dataObject.getString(KEY_KEY)
            if (key != null) {
                return ReferenceContainer(ReferenceType.Key(key))
            }

            return null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ReferenceContainer

        return referenceType == other.referenceType
    }

    override fun hashCode(): Int {
        return referenceType.hashCode()
    }
}
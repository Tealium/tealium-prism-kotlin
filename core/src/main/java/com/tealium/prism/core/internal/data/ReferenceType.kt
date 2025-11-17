package com.tealium.prism.core.internal.data

import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.DataObjectConvertible
import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.data.ReferenceContainer

/**
 * Models the different types of references for extracting variables from [DataObject]s
 */
sealed interface ReferenceType: DataObjectConvertible {

    /**
     * Returns the [ReferenceType] as a [JsonObjectPath].
     */
    fun asJsonPath(): JsonObjectPath

    /**
     * A [ReferenceType] where the variable is to be found in a [DataObject] at the given [key]
     */
    data class Key(val key: String): ReferenceType {
        override fun asJsonPath(): JsonObjectPath =
            JsonPath.Companion.root(key)

        override fun asDataObject(): DataObject =
            DataObject.create {
                put(ReferenceContainer.Converter.KEY_KEY, key)
            }
    }

    /**
     * A [ReferenceType] where the variable is to be found in a [DataObject], possibly
     * nested under other [DataObject]s or [DataList]s, according to the given [path].
     */
    data class Path(val path: JsonObjectPath): ReferenceType {
        override fun asJsonPath(): JsonObjectPath =
            path

        override fun asDataObject(): DataObject =
            DataObject.create {
                put(ReferenceContainer.Converter.KEY_PATH, path)
            }
    }
}
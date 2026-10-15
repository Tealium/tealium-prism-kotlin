package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.settings.MappingParameters
import com.tealium.prism.core.api.settings.json.TransformationOperation
import com.tealium.prism.core.api.tracking.Dispatch

typealias MappingOperation = TransformationOperation<MappingParameters>

inline val MappingOperation.path get() = parameters.reference?.path
inline val MappingOperation.filter get() = parameters.filter
inline val MappingOperation.mapTo get() = parameters.mapTo

class MappingsEngine(
    private val mappings: ObservableState<Map<String, List<MappingOperation>>>
) {
    /**
     * Fetches any mappings for the given [dispatcherId], and applies them to the [dispatch]
     *
     * If no mappings are found, then the [dispatch] is returned unaffected.
     */
    fun map(dispatcherId: String, dispatch: Dispatch): Dispatch {
        val dispatcherMappings = mappings.value[dispatcherId]
            ?: return dispatch

        return map(dispatch, dispatcherMappings)
    }

    companion object {

        private val commandNamePath = JsonPath[Dispatch.Keys.COMMAND_NAME]

        /**
         * Applies the [mappings] on top of the [dispatch] payload, replacing the payload in the
         * [dispatch], which is returned.
         *
         * See [map] for the [DataObject] overload for details on how the [mappings] are applied.
         *
         * @param dispatch The incoming [Dispatch] whose payload is expected to be mapped
         * @param mappings The list of mappings to apply, in order.
         */
        fun map(dispatch: Dispatch, mappings: List<MappingOperation>): Dispatch {
            if (mappings.isEmpty())
                return dispatch

            val remappedPayload = map(dispatch.payload(), mappings)
            dispatch.replace(remappedPayload)

            return dispatch
        }

        /**
         * Applies the [mappings] on top of the [payload], returning the resulting [DataObject].
         *
         * Keys and values that are not described in the [mappings] are kept as they are, while any value
         * already present at a mapped destination is replaced by the mapped value. For nested or list index
         * destinations, sibling keys and other list elements are left alone. If a parent along the destination
         * path holds a value of a different type, it is replaced.
         *
         * The only exception is the root [Dispatch.Keys.COMMAND_NAME] key: mapped values (constants or
         * variables) are always appended to any value already there (from the original payload or from an
         * earlier mapping), flattening lists one level, so that multiple commands can be collected.
         * For example an existing `["a"]` with a mapped `["b", ["c"]]` gives `["a", "b", ["c"]]`.
         *
         * Sources and filters are always read from the original [payload], so mappings don't chain:
         * a mapping can't read the result of another mapping, and the order of the mappings only matters
         * when they write to the same destination.
         *
         * An empty list of [mappings] returns the [payload] unchanged.
         *
         * @param payload The incoming [DataObject] whose data is expected to be mapped
         * @param mappings The list of mappings to apply, in order.
         */
        fun map(payload: DataObject, mappings: List<MappingOperation>): DataObject {
            if (mappings.isEmpty())
                return payload

            val result = payload.buildUpon()
            mappings.forEach { mapping ->
                mapDataObject(result, payload, mapping)
            }
            return result.build()
        }

        /**
         * Applies a single [mapping], reading from the original [payload], and placing the mapped item
         * into the relevant destination in the [into] [DataObject.Builder].
         *
         * If the [MappingParameters.filter] parameter is not null, then it will be compared to the
         * value taken from the [payload] according to the [MappingParameters.reference]. This comparison
         * is a String comparison of the [DataItem.value].
         *
         * If the [MappingParameters.mapTo] parameter is not null, then that will be the value
         * mapped. Otherwise the value from [payload] will be taken according to the [MappingParameters.reference]
         *
         * The mapped value replaces whatever is at the destination, unless the destination is the root
         * [Dispatch.Keys.COMMAND_NAME], in which case it is appended to it.
         */
        private fun mapDataObject(
            into: DataObject.Builder,
            payload: DataObject,
            mapping: MappingOperation
        ): DataObject.Builder {
            val mapped = getMappedValue(payload, mapping)
                ?: return into

            val destination = mapping.destination.path
            val existing = into.extract(destination)

            val itemToInsert = if (destination == commandNamePath && existing != null) {
                // merge if necessary for `command_name` key
                combineCommands(existing, mapped)
            } else {
                // always overwrite when not `command_name` key, or no existing value
                mapped
            }

            return into.buildPath(destination, itemToInsert)
        }

        private fun getMappedValue(
            payload: DataObject,
            mapping: MappingOperation
        ): DataItem? {
            val extracted = mapping.path?.let { path -> payload.extract(path) }
            val filter = mapping.filter
            if (filter != null && extracted?.value.toString() != filter.value) {
                return null
            }

            val mapTo = mapping.mapTo?.value?.asDataItem()
            return mapTo ?: extracted
        }

        /**
         * Combines incoming `command_name`s, with existing `command_name`s, such that:
         *
         * If the [existing] item is already a [DataList] then the [incoming] item is appended, and
         * the [DataList] is returned as a [DataItem].
         *
         * If the [existing] item is not a [DataList] then a new [DataList] is returned containing
         * both [existing] and [incoming]
         *
         * In either case, if [incoming] is a [DataList] itself, then its contents are all appended
         * as opposed to the [DataList] itself.
         */
        private fun combineCommands(existing: DataItem, incoming: DataItem): DataItem {
            val builder = existing.getDataList()?.buildUpon()
                ?: DataList.Builder().add(existing)

            incoming.getDataList()?.let { builder.addAll(it) }
                ?: builder.add(incoming)

            return builder.build().asDataItem()
        }
    }
}

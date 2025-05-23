package com.tealium.core.internal.dispatch

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemUtils.asDataItem
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.settings.json.TransformationOperation
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.settings.MappingParameters

typealias MappingOperation = TransformationOperation<MappingParameters>
inline val MappingOperation.key get() = parameters.key
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

        /**
         * Remaps the [dispatch] according to the provided [mappings].
         *
         * The [mappings] describe which keys and values to take from the [Dispatch.payload] and place into a new
         * [DataObject] that will replace the data inside the [dispatch], which is returned.
         *
         * The returned [Dispatch] will have a payload built from an empty [DataObject], such that keys/values
         * that are not described in the [mappings] will be lost.
         *
         * @param dispatch The incoming [Dispatch] whose payload is expected to be mapped to the resulting [Dispatch]
         * @param mappings The list of mappings to apply, in order.
         */
        fun map(dispatch: Dispatch, mappings: List<MappingOperation>): Dispatch {
            val remappedPayload = map(dispatch.payload(), mappings)
            dispatch.replace(remappedPayload)

            return dispatch
        }

        /**
         * Remaps the [payload] according to the provided [mappings].
         *
         * The [mappings] describe which keys and values to take from the [payload] and place into a new
         * [DataObject] that will be returned by this method.
         *
         * The returned [DataObject] will be built from an empty [DataObject], such that keys/values
         * that are not described in the [mappings] will be lost.
         *
         * @param payload The incoming [DataObject] whose data is expected to be mapped to the resulting [DataObject]
         * @param mappings The list of mappings to apply, in order.
         */
        fun map(payload: DataObject, mappings: List<MappingOperation>): DataObject {
            var result = DataObject.EMPTY_OBJECT

            for (mapping in mappings) {
                result = mapDataObject(result, payload, mapping)
            }

            return result
        }

        /**
         * Applies a single [mapping] to the [payload], placing all mapped items into the relevant
         * destination in the [into] [DataObject].
         *
         * If the [MappingParameters.filter] parameter is not null, then it will be compared to the
         * the value taken from the [payload] according to the [MappingParameters.key]. This comparison
         * is a String comparison of the [DataItem.value].
         *
         * If the [MappingParameters.mapTo] parameter is not null, then that will be the value
         * mapped. Otherwise the value from [payload] will be taken according to the [MappingParameters.key]
         *
         */
        private fun mapDataObject(
            into: DataObject,
            payload: DataObject,
            mapping: MappingOperation
        ): DataObject {
            val extracted = mapping.key?.let { payload.extract(it) }
            val filter = mapping.filter
            if (filter != null && extracted?.value.toString() != filter.value) {
                return into
            }

            val mapTo = mapping.mapTo?.value?.asDataItem()
            val shouldCombine = mapTo != null
            val input = mapTo ?: extracted
                ?: return into

            val (variable, path) = mapping.destination
            return buildPathAndPut(into, path ?: emptyList(), variable, input, shouldCombine)
        }

        /**
         * Recursively builds a "path" of [DataObject]s, if required, in order to store the [input]
         */
        private fun buildPathAndPut(
            into: DataObject,
            path: List<String>,
            key: String,
            input: DataItem,
            shouldCombine: Boolean
        ): DataObject {
            if (path.isEmpty()) {
                return into.copy {
                    put(key, combine(into.get(key), input, shouldCombine))
                }
            }

            val pathComponent = path.first()
            val remainingPath = path.drop(1)
            return into.copy {
                val subObj = into.getDataObject(pathComponent) ?: DataObject.EMPTY_OBJECT
                put(pathComponent, buildPathAndPut(subObj, remainingPath, key, input, shouldCombine))
            }
        }

        /**
         * Combines incoming mapped items, with existing mapped items, such that:
         *
         *  - If the [existing] item is `null`, then the `incoming` is returned.
         *  - If the [existing] item is already a [DataList] then the [incoming] is appended, and the [DataList] is returned.
         *  - If the [existing] item is not `null` then a new [DataList] is returned containing both [existing] and [incoming]
         */
        private fun combine(existing: DataItem?, incoming: DataItem, shouldCombine: Boolean): DataItem {
            // nothing mapped yet
            if (existing == null || !shouldCombine) {
                return incoming
            }

            val list = existing.getDataList()?.copy {
                // existing datalist already
                add(incoming)
            } ?: DataList.create {
                // replace existing dataitem, with list of both
                add(existing)
                add(incoming)
            }
            return list.asDataItem()
        }
    }
}
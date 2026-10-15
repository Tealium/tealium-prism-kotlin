package com.tealium.prism.core.internal.data

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataList.Companion.EMPTY_LIST
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonPath.Component

/**
 * Utility method to extract a [DataItem] from another [DataItem], according to the given [components]
 *
 * Note. this will only extract from [DataItem]s where the value is one of [DataList] or [DataObject]
 */
fun DataItem.extract(components: List<Component>): DataItem? {
    var dataItem: DataItem? = this
    for (pathComponent in components) {
        if (dataItem == null) return null

        dataItem = when (pathComponent) {
            is Component.Key -> dataItem.getDataObject()?.get(pathComponent.key)
            is Component.Index -> dataItem.getDataList()?.get(pathComponent.index)
        }
    }
    return dataItem
}

/**
 * Builds the [path] in a copy of this [DataObject] under [key], and stores the [item] there.
 * See [DataList.buildPath] for how list indexes are treated.
 */
fun DataObject.buildPath(key: String, path: List<Component>, item: DataItem): DataObject = copy {
    buildPath(key, path, item)
}

fun DataObject.Builder.buildPath(key: String, path: List<Component>, item: DataItem) : DataObject.Builder = apply {
    if (path.isEmpty()) {
        put(key, item)
        return this
    }

    val pathComponent = path.first()
    val remainingPath = path.drop(1)

    val nested = this[key]
    when(pathComponent) {
        is Component.Key -> {
            val subObj =  nested?.getDataObject() ?: DataObject.EMPTY_OBJECT
            put(key, subObj.buildPath(pathComponent.key, remainingPath, item))
        }
        is Component.Index -> {
            val list = nested?.getDataList() ?: DataList.EMPTY_LIST
            put(key, list.buildPath(pathComponent.index, remainingPath, item))
        }
    }
}

/**
 * Builds the [path] in a copy of this [DataList] at [index], and stores the [item] there.
 *
 * An [index] that already exists is replaced rather than inserted, so the following elements keep
 * their position; an [index] beyond the end pads the list with [DataItem.NULL].
 */
fun DataList.buildPath(index: Int, path: List<Component>, item: DataItem): DataList = copy {
    buildPath(index, path, item)
}

fun DataList.Builder.buildPath(index: Int, path: List<Component>, item: DataItem): DataList.Builder = apply {
    if (path.isEmpty()) {
        padWithNulls(index)
        replaceOrAdd(item, index)
        return this
    }

    val pathComponent = path.first()
    val remainingPath = path.drop(1)

    val nested = this[index]
    padWithNulls(index)

    when (pathComponent) {
        is Component.Key -> {
            val subObj = nested?.getDataObject() ?: DataObject.EMPTY_OBJECT
            replaceOrAdd(subObj.buildPath(pathComponent.key, remainingPath, item).asDataItem(), index)
        }

        is Component.Index -> {
            val list = nested?.getDataList() ?: EMPTY_LIST
            replaceOrAdd(list.buildPath(pathComponent.index, remainingPath, item).asDataItem(), index)
        }
    }
}

fun DataList.Builder.padWithNulls(until: Int) {
    while (size() < until) {
        add(DataItem.NULL)
    }
}
/**
 * Replaces the element at [index] with [item] when there is one, otherwise adds it to the list.
 *
 * Unlike [DataList.Builder.add], this never shifts the elements following [index].
 */
private fun DataList.Builder.replaceOrAdd(item: DataItem, index: Int) {
    if (index in 0 until size()) {
        remove(index)
    }
    add(item, index)
}

package com.tealium.prism.core.internal.persistence.stores

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.persistence.ReadableDataStore
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.persistence.repositories.KeyValueRepository
import java.util.LinkedList
import java.util.Queue

class ModuleStore(
    private val keyValueRepository: KeyValueRepository,
    private val onDataExpired: Observable<DataObject> = Observables.publishSubject(),
    onDataUpdated: Subject<DataObject> = Observables.publishSubject(),
    onDataRemoved: Subject<List<String>> = Observables.publishSubject(),
) : DataStore {

    private val _onDataUpdated: Subject<DataObject> = onDataUpdated
    override val onDataUpdated: Observable<DataObject>
        get() = _onDataUpdated.asObservable()

    private val _onDataRemoved: Subject<List<String>> = onDataRemoved
    override val onDataRemoved: Observable<List<String>>
        get() = Observables.merge(
            onDataExpired.map { it.getAll().keys.toList() },
            _onDataRemoved
        )

    override fun edit(): DataStore.Editor =
        DataStorageEditor(keyValueRepository, this) { edits ->
            val updates = edits.filterIsInstance(Edit.Put::class.java)
            val removals = edits.filterIsInstance(Edit.Remove::class.java)

            if (updates.isNotEmpty()) {
                val dataObject = DataObject.create {
                    updates.forEach { put ->
                        put(put.key, put.value)
                    }
                }

                _onDataUpdated.onNext(dataObject)
            }

            if (removals.isNotEmpty()) {
                val keys = removals.map { it.key }

                _onDataRemoved.onNext(keys)
            }
        }

    override fun get(key: String): DataItem? =
        keyValueRepository.get(key)

    override fun getAll(): DataObject =
        keyValueRepository.getAll().asDataObject()

    override fun keys(): List<String> =
        keyValueRepository.keys()

    override fun count(): Int =
        keyValueRepository.count()

    override fun iterator(): Iterator<Map.Entry<String, DataItem>> =
        getAll().iterator()

    class DataStorageEditor(
        private val keyValueRepository: KeyValueRepository,
        private val reader: ReadableDataStore,
        private val onCommit: (List<Edit>) -> Unit
    ) : DataStore.Editor, AutoCloseable {
        private var closed: Boolean = false
        private var committed: Boolean = false
        private var clear: Boolean = false
        private var edits: Queue<Edit> = LinkedList()

        override fun put(key: String, value: DataItem, expiry: Expiry): DataStore.Editor = apply {
            ensureNotClosed()

            edits.add(Edit.Put(key, value, expiry))
        }

        override fun putAll(dataObject: DataObject, expiry: Expiry): DataStore.Editor = apply {
            ensureNotClosed()

            dataObject.forEach { (key, value) ->
                if (value != DataItem.NULL) {
                    // Can't store nulls; ignore it.
                    edits.add(Edit.Put(key, value, expiry))
                }
            }
        }

        override fun remove(key: String): DataStore.Editor = apply {
            ensureNotClosed()

            edits.add(Edit.Remove(key))
        }


        override fun remove(keys: List<String>): DataStore.Editor = apply {
            ensureNotClosed()

            keys.forEach { key ->
                edits.add(Edit.Remove(key))
            }
        }

        override fun clear(): DataStore.Editor = apply {
            ensureNotClosed()

            clear = true
        }

        override fun commit() {
            ensureNotClosed()

            if (committed) return
            if (edits.isEmpty() && !clear) return

            committed = true

            val edits = edits.toList()
            val appliedEdits = mutableListOf<Edit>()

            try {
                keyValueRepository.transactionally { store ->
                    if (clear) {
                        appliedEdits.addAll(store.keys().map { Edit.Remove(it) })
                        store.clear()
                    }

                    edits.forEach { edit ->
                        val result: Long = when (edit) {
                            is Edit.Put -> {
                                store.upsert(edit.key, edit.value, edit.expiry)
                            }

                            is Edit.Remove -> {
                                store.delete(edit.key).toLong()
                            }
                        }
                        if (result > 0) {
                            appliedEdits.add(edit)
                        }
                    }
                }
            } catch (ex: PersistenceException) {
                throw ex
            }

            onCommit.invoke(appliedEdits)
        }

        override fun get(key: String): DataItem? {
            ensureNotClosed()

            return reader.get(key)
        }

        override fun getAll(): DataObject {
            ensureNotClosed()

            return reader.getAll()
        }

        override fun keys(): List<String> {
            ensureNotClosed()

            return reader.keys()
        }

        override fun count(): Int {
            ensureNotClosed()

            return reader.count()
        }

        override fun close() {
            closed = true
            if (!committed && (edits.isNotEmpty() || clear)) {
                // TODO - log that there are uncommitted edits
            }
        }

        private fun ensureNotClosed() {
            if (closed)
                throw DataStore.Editor.EditorClosedException("Editor is already closed.")
        }
    }

    sealed class Edit {
        class Put(val key: String, val value: DataItem, val expiry: Expiry) : Edit()
        class Remove(val key: String) : Edit()
    }
}
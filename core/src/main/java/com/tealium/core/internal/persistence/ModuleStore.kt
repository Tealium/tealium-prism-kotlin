package com.tealium.core.internal.persistence

import com.tealium.core.api.DataStore
import com.tealium.core.api.Expiry
import com.tealium.core.api.PersistenceException
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.data.TealiumValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.*

class ModuleStore(
    private val keyValueRepository: KeyValueRepository,
    private val onDataExpired: Flow<TealiumBundle> = MutableSharedFlow(),
    onDataUpdated: MutableSharedFlow<TealiumBundle> = MutableSharedFlow(),
    onDataRemoved: MutableSharedFlow<List<String>> = MutableSharedFlow(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : DataStore {

    private val _onDataUpdated: MutableSharedFlow<TealiumBundle> = onDataUpdated
    override val onDataUpdated: Flow<TealiumBundle>
        get() = _onDataUpdated.asSharedFlow()

    private val _onDataRemoved: MutableSharedFlow<List<String>> = onDataRemoved
    override val onDataRemoved: Flow<List<String>>
        get() = merge(
            onDataExpired.map { it.getAll().keys.toList() },
            _onDataRemoved
        )

    override fun edit(): DataStore.Editor =
        DataStorageEditor(keyValueRepository) { edits ->
            val updates = edits.filterIsInstance(Edit.Put::class.java)
            val removals = edits.filterIsInstance(Edit.Remove::class.java)

            if (updates.isNotEmpty()) {
                val bundle = TealiumBundle.create {
                    updates.forEach { put ->
                        put(put.key, put.value)
                    }
                }

                coroutineScope.launch {
                    _onDataUpdated.emit(bundle)
                }
            }

            if (removals.isNotEmpty()) {
                val keys = removals.map { it.key }

                coroutineScope.launch {
                    _onDataRemoved.emit(keys)
                }
            }
        }

    override fun get(key: String): TealiumValue? =
        keyValueRepository.get(key)

    override fun getAll(): TealiumBundle {
        val bundleBuilder = TealiumBundle.Builder()

        keyValueRepository.getAll().forEach { (key, value) ->
            bundleBuilder.put(key, value)
        }

        return bundleBuilder.getBundle()
    }

    override fun keys(): List<String> =
        keyValueRepository.keys()

    override fun count(): Int =
        keyValueRepository.count()

    override fun iterator(): Iterator<Map.Entry<String, TealiumValue>> =
        getAll().iterator()

    private class DataStorageEditor(
        private val keyValueRepository: KeyValueRepository,
        private val onCommit: (List<Edit>) -> Unit
    ) : DataStore.Editor {
        private var committed: Boolean = false
        private var clear: Boolean = false
        private var edits: Queue<Edit> = LinkedList()

        override fun put(key: String, value: TealiumValue, expiry: Expiry): DataStore.Editor =
            apply {
                edits.add(Edit.Put(key, value, expiry))
            }

        override fun putAll(bundle: TealiumBundle, expiry: Expiry): DataStore.Editor = apply {
            bundle.forEach { (key, value) ->
                if (value != TealiumValue.NULL) {
                    // Can't store nulls; ignore it.
                    edits.add(Edit.Put(key, value, expiry))
                }
            }
        }

        override fun remove(key: String): DataStore.Editor = apply {
            edits.add(Edit.Remove(key))
        }

        override fun remove(keys: List<String>): DataStore.Editor = apply {
            keys.forEach { key ->
                edits.add(Edit.Remove(key))
            }
        }

        override fun clear(): DataStore.Editor = apply {
            clear = true
        }

        override fun commit() {
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
                committed = false
                throw ex
            }

            onCommit.invoke(appliedEdits)
        }
    }

    private sealed class Edit {
        class Put(val key: String, val value: TealiumValue, val expiry: Expiry) : Edit()
        class Remove(val key: String) : Edit()
    }
}
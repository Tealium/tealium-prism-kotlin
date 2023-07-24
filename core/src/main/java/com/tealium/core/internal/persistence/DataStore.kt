package com.tealium.core.internal.persistence

import com.tealium.core.api.DataStore
import com.tealium.core.api.Expiry
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumValue
import java.util.*

class DataStoreImpl(
    private val dataStorageStrategy: DataStorageStrategy<String, TealiumValue>,
) : DataStore {

    override fun edit(): DataStore.Editor =
        DataStorageEditor(dataStorageStrategy)

    override fun get(key: String): TealiumValue? =
        dataStorageStrategy.get(key)

    override fun getAll(): TealiumBundle {
        val bundleBuilder = TealiumBundle.Builder()

        dataStorageStrategy.getAll().forEach { (key, value) ->
            bundleBuilder.put(key, value)
        }

        return bundleBuilder.getBundle()
    }

    override fun keys(): List<String> =
        dataStorageStrategy.keys()

    override fun count(): Int =
        dataStorageStrategy.count()

    override fun iterator(): Iterator<Map.Entry<String, TealiumValue>> =
        getAll().iterator()

    class DataStorageEditor(
        private val dataStorageStrategy: DataStorageStrategy<String, TealiumValue>,
    ) : DataStore.Editor {
        private var committed: Boolean = false
        private var clear: Boolean = false
        private var edits: Queue<(DataStorageStrategy<String, TealiumValue>) -> Unit> = LinkedList()

        override fun put(key: String, value: TealiumValue, expiry: Expiry): DataStore.Editor =
            apply {
                edits.add { store ->
                    store.upsert(key, value, expiry)
                }
            }

        override fun putAll(bundle: TealiumBundle, expiry: Expiry): DataStore.Editor = apply {
            edits.add { store ->
                bundle.forEach { (key, value) ->
                    if (value == TealiumValue.NULL) {
                        // Can't store nulls; remove it.
                        store.delete(key)
                    } else {
                        store.upsert(key, value, expiry)
                    }
                }
            }
        }

        override fun remove(key: String): DataStore.Editor = apply {
            edits.add { store ->
                store.delete(key)
            }
        }

        override fun remove(keys: List<String>): DataStore.Editor = apply {
            edits.add { store ->
                keys.forEach { key ->
                    store.delete(key)
                }
            }
        }

        override fun clear(): DataStore.Editor = apply {
            clear = true
        }

        override fun commit() {
            if (committed) return

            committed = true
            dataStorageStrategy.transactionally({ _ ->
                // TODO - perhaps allow re-commit on error.
                // committed = false
            }) { store ->
                if (clear) {
                    store.clear()
                }

                edits.forEach { edit ->
                    edit.invoke(store)
                }
            }
        }
    }
}
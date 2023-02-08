package com.tealium.core.internal.observables

import android.util.Log
import com.tealium.core.api.data.MutableObservableProperty

open class MutableObservablePropertyImpl<T, L>(
    initial: T,
    private val onUpdate: (L, T) -> Unit
) : MutableObservableProperty<T, L>, ObservablePropertyImpl<T, L>(initial) {
    override fun update(value: T) {
        this.value = value

        val observers = synchronized(mObservers) {
            try {
                ArrayList(mObservers)
            } catch (e: Exception) {
                Log.w("Copy", e.message + "")
                null
            }
        }
        observers?.forEach {
            onUpdate(it, value)
        }
    }
}
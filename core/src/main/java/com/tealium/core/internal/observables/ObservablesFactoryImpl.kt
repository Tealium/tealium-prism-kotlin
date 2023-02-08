package com.tealium.core.internal.observables

import com.tealium.core.api.data.MutableObservableProperty
import com.tealium.core.api.data.ObservablesFactory
import java.util.concurrent.ExecutorService

class ObservablesFactoryImpl(
    // TODO - add alternate default executors if necessary
    private val defaultExecutorService: ExecutorService
): ObservablesFactory {

    override fun <T, L> createProperty(
        initial: T,
        deliver: (L, T) -> Unit,
        block: (ObservablesFactory.Builder<T, L>.() -> Unit)?
    ): MutableObservableProperty<T, L> {
        val builder = Builder<T, L>(initial)
            .onUpdate(deliver)

        block?.invoke(builder)

        return builder.build()
    }

    override fun <T, L> createBufferedProperty(
        initial: T,
        size: Int,
        deliver: (L, T) -> Unit,
        block: (ObservablesFactory.Builder<T, L>.() -> Unit)?
    ): MutableObservableProperty<T, L> {
        if (size < 1) throw IllegalArgumentException("size must be greater than 0")

        val builder = if (size <= 1) {
            Builder<T, L>(initial)
                .onUpdate(deliver)
        } else {
            Builder<T, L>(initial)
                .onUpdate(deliver)
                .buffer(size)
        }

        block?.invoke(builder)

        return builder.build()
    }

    inner class Builder<T, L>(
        private var initialValue: T
    ): ObservablesFactory.Builder<T, L> {
        private var executor: ExecutorService? = defaultExecutorService
        private var onUpdate: ((L, T) -> Unit)? = null
        private var size: Int? = null

        override fun executor(executorService: ExecutorService?) = apply {
            this.executor = executorService
        }

        override fun onUpdate(onUpdate: ((L, T) -> Unit)) = apply { this.onUpdate = onUpdate }
        override fun buffer(size: Int) = apply { this.size = size }

        override fun build(): MutableObservableProperty<T, L> {
            val onUpdate: (L, T) -> Unit = { listener, value ->
                val task = Runnable {
                    onUpdate?.invoke(listener, value)
                }
                executor?.submit(task) ?: task.run()
            }
            val bufferSize = size
            return if (bufferSize != null && bufferSize > 1) {
                BufferedMutableObservablePropertyImpl(
                    initial = initialValue,
                    bufferSize = bufferSize,
                    onUpdate = onUpdate
                )
            } else {
                MutableObservablePropertyImpl(
                    initial = initialValue,
                    onUpdate = onUpdate
                )
            }
        }
    }
}
package com.tealium.core.internal

/**
 * Models a singleton instance that allows for a single parameter to be provided for more complex
 * construction.
 *
 * The [getInstance] method uses double-checked synchronization to ensure only a single instance of
 * the inheritor class is created.
 */
abstract class Singleton<out T, in A>(private val creator: (A) -> T) {

    @Volatile
    private var instance: T? = null

    /**
     * Returns the instance if already created, or constructs a new one and stores it for later use.
     */
    fun getInstance(arg: A): T {
        return instance ?: synchronized(this) {
            instance ?: creator.invoke(arg).also {
                instance = it
            }
        }
    }
}
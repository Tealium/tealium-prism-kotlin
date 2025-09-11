package com.tealium.tests.common

import org.junit.Assert.fail
import kotlin.reflect.KClass

/**
 * JUnit helper to assert multiple levels of Exceptions
 */
inline fun <reified T : Throwable> assertThrows(
    cause: KClass<out Throwable>? = null,
    block: () -> Unit
) {
    try {
        block.invoke()
    } catch (e: Exception) {
        if (!T::class.java.isInstance(e)) {
            fail("Expected exception of type ${T::class.qualifiedName}, but got ${e::class.qualifiedName}")
        }
        if (cause != null && !cause.java.isInstance(e.cause)) {
            fail("Expected cause of type ${cause.qualifiedName}, but got ${e.cause?.let { it::class.qualifiedName } ?: "null"}")
        }
        return
    }

    fail("Expected exception of type ${T::class.qualifiedName}, but nothing was thrown")
}
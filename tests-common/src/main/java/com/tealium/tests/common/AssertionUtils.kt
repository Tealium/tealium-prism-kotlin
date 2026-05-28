package com.tealium.tests.common

import org.junit.Assert.fail
import kotlin.reflect.KClass

/**
 * JUnit helper to assert multiple levels of Exceptions
 */
inline fun <reified T : Throwable> assertThrows(
    cause: KClass<out Throwable>? = null,
    block: () -> Unit
): T {
    try {
        block.invoke()
    } catch (e: Exception) {
        if (!T::class.java.isInstance(e)) {
            fail("Expected exception of type ${T::class.qualifiedName}, but got ${e::class.qualifiedName}")
        }
        if (cause != null && !cause.java.isInstance(e.cause)) {
            fail("Expected cause of type ${cause.qualifiedName}, but got ${e.cause?.let { it::class.qualifiedName } ?: "null"}")
        }
        return e as T
    }

    throw AssertionError("Expected exception of type ${T::class.qualifiedName}, but nothing was thrown")
}

inline fun assertWithTimeout(
    timeoutMillis: Long = 1_000L,
    delay: Long = timeoutMillis / 10,
    predicate: () -> Boolean
) {
    var currentTime = System.currentTimeMillis()
    val timeoutTime = currentTime + timeoutMillis
    while (!predicate()) {
        if (currentTime > timeoutTime)
            fail("Assertion timed out")

        Thread.sleep(delay)
        currentTime = System.currentTimeMillis()
    }
}
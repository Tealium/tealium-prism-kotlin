package com.tealium.tests.common

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private fun newRaceExecutor(): ExecutorService =
    Executors.newSingleThreadExecutor { Thread(it, "race-worker").apply { isDaemon = true } }

// Shared and reused across calls: it avoids starting a thread per iteration, and it is the setup
// the detection rates were measured with.
private var raceExecutor = newRaceExecutor()

/**
 * Runs [r1] and [r2] at (nearly) the same instant, [r1] on a background thread and [r2] on the
 * calling thread, and returns once both have finished. Based on RxJava's `TestHelper.race`.
 *
 * Both sides spin on a shared gate until the other arrives, so they usually start within
 * nanoseconds of each other. Do not add `Thread.yield()` to the spin: it measurably
 * lowers the chance of hitting a race on a loaded machine.
 *
 * This only makes a race likely, not certain, so call it in a loop (thousands of iterations) and
 * assert something that holds in every interleaving. Keep logging and other slow, shared-lock
 * code out of [r1] and [r2], as that serialises the two sides and hides the race.
 *
 * A [Throwable] thrown by either side is rethrown; if both throw, the second is added as
 * suppressed to the first. Fails with an [AssertionError] if [r1] has not finished 5 seconds after
 * [r2] returns. [r2] itself runs unbounded on the calling thread, so give any test whose [r2] could
 * block an outer timeout, for example a JUnit [org.junit.rules.Timeout] rule.
 */
@Synchronized
fun race(r1: () -> Unit, r2: () -> Unit) {
    val gate = AtomicInteger(2)
    val done = CountDownLatch(2)
    val errors = arrayOfNulls<Throwable>(2)

    raceExecutor.execute {
        if (gate.decrementAndGet() != 0) {
            while (gate.get() != 0) {
            }
        }
        try {
            r1()
        } catch (t: Throwable) {
            errors[0] = t
        }
        done.countDown()
    }
    if (gate.decrementAndGet() != 0) {
        while (gate.get() != 0) {
        }
    }
    try {
        r2()
    } catch (t: Throwable) {
        errors[1] = t
    }
    done.countDown()

    if (!done.await(5, TimeUnit.SECONDS)) {
        // Don't let a stuck r1 block the worker thread for later calls.
        raceExecutor.shutdownNow()
        raceExecutor = newRaceExecutor()
        throw AssertionError("race() timed out after 5 seconds")
    }
    val first = errors[0]
    val second = errors[1]
    if (first != null) {
        second?.let(first::addSuppressed)
        throw first
    }
    if (second != null) throw second
}

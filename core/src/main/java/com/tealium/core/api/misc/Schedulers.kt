package com.tealium.core.api.misc

interface Schedulers {
    /**
     * Executes tasks on the Android main [Thread].
     */
    val main: Scheduler

    /**
     * Tealium's background processing queue for keeping events in order.
     */
    val tealium: Scheduler

    /**
     * Worker queue for longer running tasks - useful for blocking http requests.
     */
    val io: Scheduler
}
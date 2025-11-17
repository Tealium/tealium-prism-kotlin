package com.tealium.prism.core.api.misc

/**
 * Constants for the environments defined by a standard Tealium profile. Used predominantly to
 * differentiate between settings that are not yet ready for a production app, and those which are.
 */
object Environment {

    /**
     * Typically used during development when testing out iterative settings updates.
     *
     * Not intended for use in production applications.
     */
    const val DEV = "dev"

    /**
     * Typically used during testing to validate that the deployment is functioning correctly ahead
     * of release to production.
     *
     * Not intended for use in production applications.
     */
    const val QA = "qa"

    /**
     * Typically used for production applications, where any configured settings are confirmed to be
     * correct at the time of publication.
     */
    const val PROD = "prod"
}
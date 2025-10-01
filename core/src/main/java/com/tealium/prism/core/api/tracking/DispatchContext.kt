package com.tealium.prism.core.api.tracking

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module

/**
 * Provides some meta-information about the [Dispatch] being tracked.
 *
 * The [source] describes where the [Dispatch] has originated from.
 *
 * The [initialData] is the [DataObject] created by the [Dispatch] when it was created - so it will
 * contain all user-supplied key-value data, as well as various default data points that are required
 * by the Tealium platform, e.g. "tealium_account", "tealium_profile" ...
 *
 * @param source Indicates where the [Dispatch] has originated from.
 * @param initialData The initial [DataObject] that was created when the [Dispatch] was tracked.
 * @see Source
 */
data class DispatchContext(
    val source: Source,
    val initialData: DataObject
) {

    /**
     * Identifies the source of a [Dispatch]
     */
    class Source private constructor(private val moduleClass: Class<out Module>?) {
        // note. intentionally not a sealed class to avoid breaking changes on adding new types.

        /**
         * @return true if this [Source] indicates it was from the Application; else false
         */
        fun isFromApplication(): Boolean =
            this.moduleClass == null

        /**
         * @return true if this [Source] indicates it was from the specified [moduleClass]; else false
         */
        fun isFromModule(moduleClass: Class<out Module>): Boolean =
            this.moduleClass == moduleClass

        companion object {
            private val appSource = Source(null)

            /**
             * Returns a [Source] that indicates it was tracked from the Application
             */
            @JvmStatic
            fun application() : Source {
                return appSource
            }

            /**
             * Returns a [Source] that indicates it was tracked from a [Module] of the given [moduleClass]
             */
            @JvmStatic
            fun module(moduleClass: Class<out Module>): Source {
                return Source(moduleClass)
            }
        }
    }
}
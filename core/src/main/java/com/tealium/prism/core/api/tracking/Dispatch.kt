package com.tealium.prism.core.api.tracking

import android.os.Build
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.tracking.Dispatch.Companion.create
import com.tealium.prism.core.internal.persistence.database.getTimestampMilliseconds
import java.util.UUID

/**
 * A representation of an event or view being tracked.
 */
class Dispatch private constructor(
    private var dataObject: DataObject,
    /**
     * The unique id of this [Dispatch].
     *
     * Also stored in [Keys.REQUEST_UUID]
     */
    val id: String,
    /**
     * The difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC
     *
     * Also stored in [Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS]
     */
    val timestamp: Long,
) {

    /**
     * The event name of the [Dispatch], if it's available in the payload.
     */
    val tealiumEvent: String?
        get() = dataObject.getString(Keys.TEALIUM_EVENT)

    /**
     * The type of [Dispatch], if it's available in the payload.
     */
    val type: DispatchType?
        get() = dataObject.get(Keys.TEALIUM_EVENT_TYPE, DispatchType.Converter)

    /**
     * Returns the current payload of this [Dispatch]
     */
    fun payload(): DataObject {
        return dataObject
    }

    /**
     * Adds all of the [data] into the existing [payload], overwriting where necessary.
     */
    fun addAll(data: DataObject) {
        dataObject = dataObject.copy {
            putAll(data)
        }
    }

    /**
     * Replaces the existing [payload] with the incoming [data]
     *
     * @param data the new [DataObject] to use for the [payload]
     */
    fun replace(data: DataObject) {
        dataObject = data
    }

    /**
     * Returns a consistent short description of the [Dispatch] to help identify it throughout the logs.
     */
    fun logDescription(): String {
        return "${id.substring(0, 5)}-${tealiumEvent}"
    }

    companion object {

        /**
         * Creates a new [Dispatch] with the provided event name and context data.
         *
         * This method will automatically add common data points such as
         * [Keys.TEALIUM_EVENT]
         * [Keys.TEALIUM_EVENT_TYPE]
         * [Keys.REQUEST_UUID]
         * [Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS]
         */
        @JvmOverloads
        @JvmStatic
        fun create(
            eventName: String,
            type: DispatchType = DispatchType.Event,
            dataObject: DataObject = DataObject.EMPTY_OBJECT
        ): Dispatch {
            val uuid = UUID.randomUUID().toString()
            val timestamp = getTimestampMilliseconds()

            val updatedDataObject: DataObject = DataObject.Builder()
                .put(Keys.TEALIUM_EVENT, eventName)
                .put(Keys.TEALIUM_EVENT_TYPE, type.friendlyName)
                .put(Keys.REQUEST_UUID, uuid)
                .put(Keys.TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS, timestamp)
                .putAll(dataObject)
                .build()

            return Dispatch(
                dataObject = updatedDataObject,
                id = uuid,
                timestamp = timestamp
            )
        }

        /**
         * Produces a copy of the provided [dispatch] such that mutations to [dispatch] do not
         * also mutate the returned [Dispatch]
         */
        internal fun create(dispatch: Dispatch): Dispatch {
            return Dispatch(
                dispatch.payload(),
                dispatch.id,
                dispatch.timestamp
            )
        }

        /**
         * Creates a Dispatch from just the [id], [dataObject] and [timestamp]
         * This is expected to be used for recreating dispatches from disk, where the insertion of
         * expected key-value data is not required on construction. That is, common event data such
         * as "tealium_event" and "request_uuid" has already been added previously.
         *
         * For creating new Dispatch instances that do require those data points to be added, you
         * should use [create]
         */
        internal fun create(id: String, dataObject: DataObject, timestamp: Long): Dispatch {
            return Dispatch(
                dataObject = dataObject,
                id = id,
                timestamp = timestamp
            )
        }
    }

    object Keys {
        /**
         * The type of the event being tracked
         *
         * e.g. "link" or "view"
         */
        const val TEALIUM_EVENT_TYPE = "tealium_event_type"

        /**
         * The name of the event being tracked
         */
        const val TEALIUM_EVENT = "tealium_event"

        /**
         * The trace id used with Tealium Trace to identify events
         *
         * Legacy, see [TEALIUM_TRACE_ID]
         */
        const val CP_TRACE_ID = "cp.trace_id"

        /**
         * The trace id used with Tealium Trace to identify events
         */
        const val TEALIUM_TRACE_ID = "tealium_trace_id"

        /**
         * The name of the event being tracked
         *
         * Legacy, see [TEALIUM_EVENT]
         */
        const val EVENT = "event"

        /**
         * The full deep link URL that was used to open the application
         */
        const val DEEP_LINK_URL = "deep_link_url"

        /**
         * A prefix that is prepended to each parameter of a deep link when storing
         */
        const val DEEP_LINK_QUERY_PREFIX = "deep_link_param"

        /**
         * The referrer URL, if available, of a deep link that opened the application.
         */
        const val DEEP_LINK_REFERRER_URL = "deep_link_referrer_url"

        /**
         * A unique UUID to identify a specific event
         */
        const val REQUEST_UUID = "request_uuid"

        // AppCollector

        /**
         * A persistent, unique UUID to identify a specific app installation.
         */
        const val APP_UUID = "app_uuid"

        /**
         * The application package name.
         *
         * e.g. com.example.app
         */
        const val APP_RDNS = "app_rdns"

        /**
         * The application name as given by the application label
         */
        const val APP_NAME = "app_name"

        /**
         * The version code for the application
         */
        const val APP_BUILD = "app_build"

        /**
         * The version name for the application
         */
        const val APP_VERSION = "app_version"

        /**
         * The memory used by the application process, in megabytes
         */
        const val APP_MEMORY_USAGE = "app_memory_usage"

        // ConnectivityCollector

        /**
         * A string describing the type of connection currently in use.
         *
         * e.g. "wifi", "cellular", "none" or "unknown"
         */
        const val CONNECTION_TYPE = "connection_type"

        // DeviceCollector

        /**
         * The [DEVICE_MODEL] and [DEVICE_MANUFACTURER] in a single string
         */
        const val DEVICE = "device"

        /**
         * The device's model as given by [Build.MODEL]
         */
        const val DEVICE_MODEL = "device_model"

        /**
         * The device's manufacturer as given by [Build.MANUFACTURER]
         */
        const val DEVICE_MANUFACTURER = "device_manufacturer"

        /**
         * The device's architecture; 64bit or 32bit
         */
        const val DEVICE_ARCHITECTURE = "device_architecture"

        /**
         * The value of the `os.arch` [System] property, or `unknown`
         *
         * e.g. x86, armv7l
         */
        const val DEVICE_CPU_TYPE = "device_cputype"

        /**
         * The actual pixel resolution of the device; px
         */
        const val DEVICE_RESOLUTION = "device_resolution"

        /**
         * The logical resolution of the device; dp
         */
        const val DEVICE_LOGICAL_RESOLUTION = "device_logical_resolution"

        /**
         * The runtime version of the system.
         *
         * The value of the `java.vm.version` [System] property, or `unknown`
         */
        const val DEVICE_RUNTIME = "device_android_runtime"

        /**
         * The type of device sending the event
         *
         * e.g. mobile, tv
         */
        const val DEVICE_ORIGIN = "origin"

        /**
         * The platform name; always "android"
         */
        const val DEVICE_PLATFORM = "platform"

        /**
         * The name of the OS; always "Android"
         */
        const val DEVICE_OS_NAME = "os_name"

        /**
         * The build string of the OS, as given by [Build.VERSION.INCREMENTAL]
         */
        const val DEVICE_OS_BUILD = "device_os_build"

        /**
         * The version of the OS, as given by [Build.VERSION.RELEASE]
         */
        const val DEVICE_OS_VERSION = "device_os_version"

        /**
         * The number of bytes available to the device in its internal storage.
         */
        const val DEVICE_AVAILABLE_SYSTEM_STORAGE = "device_free_system_storage"

        /**
         * The number of bytes available to the device in its external storage.
         */
        const val DEVICE_AVAILABLE_EXTERNAL_STORAGE = "device_free_external_storage"

        /**
         * The current orientation of the device.
         *
         * One of:
         *  - "Landscape Left"
         *  - "Landscape Right"
         *  - "Portrait"
         *  - "Portrait Upside Down"
         */
        const val DEVICE_ORIENTATION = "device_orientation"

        /**
         * The default language of the device in the form of a BCP-47 language tag.
         *
         * e.g. en-US, en-GB, fr-CA
         */
        const val DEVICE_LANGUAGE = "device_language"

        /**
         * An integer value between 0..100 to describe the current charge of the user's battery.
         */
        const val DEVICE_BATTERY_PERCENT = "device_battery_percent"

        /**
         * A boolean describing whether or not the device is currently charging.
         */
        const val DEVICE_ISCHARGING = "device_ischarging"

        // TealiumCollector

        /**
         * The account name that an event is to be processed by in the Tealium platform.
         */
        const val TEALIUM_ACCOUNT = "tealium_account"

        /**
         * The profile name that an event is to be processed by in the Tealium platform.
         */
        const val TEALIUM_PROFILE = "tealium_profile"

        /**
         * A [String] identifying what environment an event has originated from. Typically used to
         * differentiate between production and development events.
         */
        const val TEALIUM_ENVIRONMENT = "tealium_environment"

        /**
         * An optional data source id, to easily identify the source of any events on the Tealium
         * platform.
         */
        const val TEALIUM_DATASOURCE_ID = "tealium_datasource"

        /**
         * Contains a UUID-like string used for identifying the visitor across the Tealium platform.
         */
        const val TEALIUM_VISITOR_ID = "tealium_visitor_id"

        /**
         * Contains the library name of the core Tealium SDK.
         */
        const val TEALIUM_LIBRARY_NAME = "tealium_library_name"

        /**
         * Contains the version number of the core Tealium SDK.
         */
        const val TEALIUM_LIBRARY_VERSION = "tealium_library_version"

        /**
         * A random 16-digit integer
         */
        const val TEALIUM_RANDOM = "tealium_random"

        /**
         * An array of the [Module.id]'s of all enabled [Module]s.
         *
         * The version numbers of these [Module]s are contained in [ENABLED_MODULES_VERSIONS]
         */
        const val ENABLED_MODULES = "enabled_modules"

        /**
         * An array of the version numbers of all enabled [Module]s.
         *
         * The id's of these [Module]s are contained in [ENABLED_MODULES]
         */
        const val ENABLED_MODULES_VERSIONS = "enabled_modules_versions"

        // TimeCollector

        /**
         * The difference, measured in seconds, between the current time and midnight, January 1, 1970 UTC
         */
        const val TEALIUM_TIMESTAMP_EPOCH = "tealium_timestamp_epoch"

        /**
         * The difference, measured in milliseconds, between the current time and midnight, January 1, 1970 UTC
         */
        const val TEALIUM_TIMESTAMP_EPOCH_MILLISECONDS = "tealium_timestamp_epoch_milliseconds"

        /**
         * Returns an ISO-8601 local date format using device default as the time zone
         *
         * e.g. '2011-12-03T10:15:30'
         */
        const val TEALIUM_TIMESTAMP_LOCAL = "tealium_timestamp_local"

        /**
         * Returns an ISO-8601 date format using device default as the time zone, including the timezone
         * offset.
         *
         * e.g. '2011-12-03T10:15:30+01:00'
         */
        const val TEALIUM_TIMESTAMP_LOCAL_WITH_OFFSET = "tealium_timestamp_local_with_offset"

        /**
         * The timezone offset as a decimal, in hours. Examples:
         * ```
         * +08:00 == 8
         * +05:45 == 5.75
         * -04:00 == -4
         * ```
         */
        const val TEALIUM_TIMESTAMP_OFFSET = "tealium_timestamp_offset"

        /**
         * The timezone identifier, e.g. `Europe/London`
         */
        const val TEALIUM_TIMESTAMP_TIMEZONE = "tealium_timestamp_timezone"

        /**
         * The ISO-8601 date format using UTC as the time zone
         *
         * e.g. '2011-12-03T10:15:30Z'
         */
        const val TEALIUM_TIMESTAMP_UTC = "tealium_timestamp_utc"

        // ConsentManager

        /**
         * The type of consent decision that has been made; "implicit" or "explicit"
         */
        const val CONSENT_TYPE = "tci.consent_type"

        /**
         * A list of all consented purposes, both processed and unprocessed.
         */
        const val ALL_CONSENTED_PURPOSES = "tci.purposes_with_consent_all"

        /**
         * A list of consented purposes that have already been processed.
         */
        const val PROCESSED_PURPOSES = "tci.purposes_with_consent_processed"

        /**
         * A list of consented purposes that are yet to be processed.
         */
        const val UNPROCESSED_PURPOSES = "tci.purposes_with_consent_unprocessed"

        // Session info

        /**
         * A [Long] value containing the time, measured in seconds, since midnight 01-01-1970
         */
        const val TEALIUM_SESSION_ID = "tealium_session_id"

        /**
         * A [Boolean] value of `true` to indicate that this event was the first event of a new session
         */
        const val IS_NEW_SESSION = "is_new_session"

        /**
         * A [Long] value of containing the session timeout measured in milliseconds
         */
        const val TEALIUM_SESSION_TIMEOUT = "_dc_ttl_"
    }
}
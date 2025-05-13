package com.tealium.core.api.tracking

import com.tealium.core.api.data.DataObject
import com.tealium.core.internal.persistence.database.getTimestampMilliseconds
import java.util.*

class Dispatch private constructor(
    private var dataObject: DataObject,
    val id: String,
    val timestamp: Long,
) {

    val tealiumEvent: String?
        get() = dataObject.getString(Keys.TEALIUM_EVENT)

    val type: TealiumDispatchType?
        get() = dataObject.get(Keys.TEALIUM_EVENT_TYPE, TealiumDispatchType.Converter)

    fun payload(): DataObject {
        return dataObject
    }

    fun addAll(data: DataObject) {
        dataObject = dataObject.copy {
            putAll(data)
        }
    }

    fun replace(data: DataObject) {
        dataObject = data
    }

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
         * [Keys.TIMESTAMP]
         */
        @JvmOverloads
        @JvmStatic
        fun create(
            eventName: String,
            type: TealiumDispatchType = TealiumDispatchType.Event,
            dataObject: DataObject = DataObject.EMPTY_OBJECT
        ): Dispatch {
            val uuid = UUID.randomUUID().toString()
            val timestamp = getTimestampMilliseconds()

            val updatedDataObject: DataObject = DataObject.Builder()
                .put(Keys.TEALIUM_EVENT, eventName)
                .put(Keys.TEALIUM_EVENT_TYPE, type.friendlyName)
                .put(Keys.REQUEST_UUID, uuid)
                .put(Keys.TIMESTAMP, timestamp)
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
        internal fun create(id: String, dataObject: DataObject, timestamp: Long): Dispatch? {
            return Dispatch(
                dataObject = dataObject,
                id = id,
                timestamp = timestamp
            )
        }
    }

    object Keys {
        const val TEALIUM_EVENT_TYPE = "tealium_event_type"
        const val TEALIUM_EVENT = "tealium_event"
        const val SCREEN_TITLE = "screen_title"
        const val LIBRARY_VERSION = "library_version"
        const val TRACE_ID = "cp.trace_id"
        const val TEALIUM_TRACE_ID = "tealium_trace_id"
        const val EVENT = "event"
        const val DEEP_LINK_URL = "deep_link_url"
        const val DEEP_LINK_QUERY_PREFIX = "deep_link_param"
        const val DEEP_LINK_REFERRER_URL = "deep_link_referrer_url"
        const val REQUEST_UUID = "request_uuid"
        const val WAS_QUEUED = "was_queued"

        // AppCollector
        const val APP_UUID = "app_uuid"
        const val APP_RDNS = "app_rdns"
        const val APP_NAME = "app_name"
        const val APP_BUILD = "app_build"
        const val APP_VERSION = "app_version"
        const val APP_MEMORY_USAGE = "app_memory_usage"

        // ConnectivityCollector
        const val CONNECTION_TYPE = "connection_type"
        const val IS_CONNECTED = "device_connected"
        const val CARRIER = "carrier"
        const val CARRIER_ISO = "carrier_iso"
        const val CARRIER_MCC = "carrier_mcc"
        const val CARRIER_MNC = "carrier_mnc"

        // DeviceCollector
        const val DEVICE = "device"
        const val DEVICE_MODEL = "device_model"
        const val DEVICE_MANUFACTURER = "device_manufacturer"
        const val DEVICE_ARCHITECTURE = "device_architecture"
        const val DEVICE_CPU_TYPE = "device_cputype"
        const val DEVICE_RESOLUTION = "device_resolution"
        const val DEVICE_LOGICAL_RESOLUTION = "device_logical_resolution"
        const val DEVICE_RUNTIME = "device_android_runtime"
        const val DEVICE_ORIGIN = "origin"
        const val DEVICE_PLATFORM = "platform"
        const val DEVICE_OS_NAME = "os_name"
        const val DEVICE_OS_BUILD = "device_os_build"
        const val DEVICE_OS_VERSION = "device_os_version"
        const val DEVICE_AVAILABLE_SYSTEM_STORAGE = "device_free_system_storage"
        const val DEVICE_AVAILABLE_EXTERNAL_STORAGE = "device_free_external_storage"
        const val DEVICE_ORIENTATION = "device_orientation"
        const val DEVICE_LANGUAGE = "device_language"
        const val DEVICE_BATTERY_PERCENT = "device_battery_percent"
        const val DEVICE_ISCHARGING = "device_ischarging"

        // ModuleCollector
        const val ENABLED_MODULES = "enabled_modules"
        const val ENABLED_MODULES_VERSIONS = "enabled_modules_versions"

        // SessionCollector
//    const val TEALIUM_SESSION_ID = Session.KEY_SESSION_ID

        // TealiumCollector
        const val TEALIUM_ACCOUNT = "tealium_account"
        const val TEALIUM_PROFILE = "tealium_profile"
        const val TEALIUM_ENVIRONMENT = "tealium_environment"
        const val TEALIUM_DATASOURCE_ID = "tealium_datasource"
        const val TEALIUM_VISITOR_ID = "tealium_visitor_id"
        const val TEALIUM_LIBRARY_NAME = "tealium_library_name"
        const val TEALIUM_LIBRARY_VERSION = "tealium_library_version"
        const val TEALIUM_RANDOM = "tealium_random"

        // TimeCollector
        const val TIMESTAMP = "timestamp"
        const val TIMESTAMP_LOCAL = "timestamp_local"
        const val TIMESTAMP_OFFSET = "timestamp_offset"
        const val TIMESTAMP_UNIX = "timestamp_unix"
        const val TIMESTAMP_UNIX_MILLISECONDS = "timestamp_unix_milliseconds"
        const val TIMESTAMP_EPOCH = "timestamp_epoch"

        // ConsentManager
        // TODO - tidy up?
        const val CONSENT_POLICY = "policy"
        const val CONSENT_STATUS = "consent_status"
        const val CONSENT_CATEGORIES = "consent_categories"
        const val CONSENT_DO_NOT_SELL = "do_not_sell"
        const val CONSENT_LAST_UPDATED = "consent_last_updated"

        // new consent manager
        const val CONSENT_TYPE = "consent_type"
        const val PURPOSES_WITH_CONSENT_ALL = "purposes_with_consent_all"
        const val PURPOSES_WITH_CONSENT_PROCESSED = "purposes_with_consent_processed"
        const val PURPOSES_WITH_CONSENT_UNPROCESSED = "purposes_with_consent_unprocessed"

        // TimedEvents
        const val TIMED_EVENT_NAME = "timed_event_name"
        const val TIMED_EVENT_START = "timed_event_start"
        const val TIMED_EVENT_END = "timed_event_end"
        const val TIMED_EVENT_DURATION = "timed_event_duration"
    }
}
package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.inSeconds
import com.tealium.prism.core.internal.settings.CoreSettingsImpl

class CoreSettingsBuilder {
    private val builder = DataObject.Builder()
    fun setLogLevel(logLevel: LogLevel) = apply {
        builder.put(CoreSettingsImpl.KEY_LOG_LEVEL, logLevel)
    }

    fun setMaxQueueSize(queueSize: Int) = apply {
        builder.put(CoreSettingsImpl.KEY_MAX_QUEUE_SIZE, queueSize)
    }

    fun setExpiration(expiration: TimeFrame) = apply {
        builder.put(CoreSettingsImpl.KEY_EXPIRATION, expiration.inSeconds())
    }

    fun setRefreshInterval(refreshInterval: TimeFrame) = apply {
        builder.put(CoreSettingsImpl.KEY_REFRESH_INTERVAL, refreshInterval.inSeconds())
    }

    fun setVisitorIdentityKey(key: String) = apply {
        builder.put(CoreSettingsImpl.KEY_VISITOR_IDENTITY_KEY, key)
    }

    fun setSessionTimeout(sessionTimeout: TimeFrame) = apply {
        builder.put(CoreSettingsImpl.KEY_SESSION_TIMEOUT, sessionTimeout.inSeconds())
    }

    fun build(): DataObject {
        return builder.build()
    }
}
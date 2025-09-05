package com.tealium.core.internal.session

import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemConverter
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataObjectConvertible
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.inMilliseconds
import com.tealium.core.api.session.Session

data class SessionInfo(
    val sessionId: Long,
    val lastEventTimeMilliseconds: Long,
    val eventCount: Int = 0
) : DataObjectConvertible {

    fun asSession(status: Session.Status) : Session {
        return Session(status, sessionId, lastEventTimeMilliseconds, eventCount)
    }

    override fun asDataObject(): DataObject =
        DataObject.create {
            put(Converter.KEY_SESSION_ID, sessionId)
            put(Converter.KEY_SESSION_LAST_EVENT, lastEventTimeMilliseconds)
            put(Converter.KEY_SESSION_EVENT_COUNT, eventCount)
        }

    fun isExpired(currentTimeMilliseconds: Long, sessionTimeout: TimeFrame): Boolean =
        lastEventTimeMilliseconds < currentTimeMilliseconds - sessionTimeout.inMilliseconds()

    object Converter : DataItemConverter<SessionInfo> {
        const val KEY_SESSION_ID = "id"
        const val KEY_SESSION_LAST_EVENT = "last_event"
        const val KEY_SESSION_EVENT_COUNT = "event_count"

        override fun convert(dataItem: DataItem): SessionInfo? {
            val dataObject = dataItem.getDataObject() ?: return null

            val sessionId = dataObject.getLong(KEY_SESSION_ID)
            val lastEventTime = dataObject.getLong(KEY_SESSION_LAST_EVENT)
            val eventCount = dataObject.getInt(KEY_SESSION_EVENT_COUNT)

            if (sessionId == null || lastEventTime == null || eventCount == null) {
                return null
            }

            return SessionInfo(sessionId, lastEventTime, eventCount)
        }
    }
}
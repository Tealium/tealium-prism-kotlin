package com.tealium.core

import android.util.Log
import java.util.*

enum class LogLevel(val level: Int) {
    DEV(Log.VERBOSE),
    QA(Log.INFO),
    PROD(Log.ASSERT),
    SILENT(Integer.MAX_VALUE);

    companion object {
        fun fromString(string: String) : LogLevel {
            return when(string.toLowerCase(Locale.ROOT)) {
                "dev" -> DEV
                "qa" -> QA
                "prod" -> PROD
                "silent" -> SILENT
                else -> PROD
            }
        }
    }
}
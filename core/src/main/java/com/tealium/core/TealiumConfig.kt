package com.tealium.core

import android.app.Application
import com.tealium.core.api.listeners.Listener
import com.tealium.core.api.ModuleFactory
import java.io.File

class TealiumConfig @JvmOverloads constructor(
    val application: Application,
    val accountName: String,
    val profileName: String,
    val environment: Environment,
    val fileName: String,
    val modules: List<ModuleFactory>,
    val events: List<Listener> = emptyList(),
//    val remoteCommands: List<RemoteCommand>
) {
    private val pathName =
        "${application.filesDir}${File.separatorChar}tealium${File.separatorChar}${accountName}${File.separatorChar}${profileName}${File.separatorChar}${environment.environment}"
    val tealiumDirectory: File = File(pathName)
}
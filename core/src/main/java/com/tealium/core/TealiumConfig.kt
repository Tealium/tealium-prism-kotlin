package com.tealium.core

import android.app.Application
import com.tealium.core.api.Listener
import com.tealium.core.api.ModuleFactory

class TealiumConfig(
    val application: Application,
    val fileName: String,
    val modules: List<ModuleFactory>,
    val events: List<Listener> = emptyList(),
//    val remoteCommands: List<RemoteCommand>
) {

}
package com.tealium.core.api

interface Listener {}

interface DispatchReadyListener : Listener {
    fun onDispatchReady(dispatch: Dispatch)
}

interface DispatchSendListener : Listener {
    fun onDispatchSend(dispatch: Dispatch)
}

interface BatchDispatchSendListener : Listener {
    fun onBatchDispatchSend(dispatch: List<Dispatch>) // TODO update to BatchDispatch?
}

interface ModuleSettingsUpdatedListener : Listener {
    fun onModuleSettingsUpdated(coreSettings: CoreSettings, moduleSettings: ModuleSettings)
}
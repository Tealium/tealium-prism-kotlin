package com.tealium.core.internal

import com.tealium.core.api.ConsentManager
import com.tealium.core.api.Module
import com.tealium.core.internal.modules.ModuleManagerImpl
import java.lang.ref.WeakReference


class ConsentManagerWrapper(
    private val moduleManagerImpl: WeakReference<ModuleManagerImpl>
) : ConsentManager {

}

class ConsentManagerImpl: ConsentManager, Module {


    override val name: String
        get() = "ConsentManager"
    override val version: String
        get() = "" //TODO("Not yet implemented")
}

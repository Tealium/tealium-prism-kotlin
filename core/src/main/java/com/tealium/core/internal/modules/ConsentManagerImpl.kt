package com.tealium.core.internal.modules

import com.tealium.core.BuildConfig
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.modules.consent.ConsentManager
import com.tealium.core.api.modules.consent.ConsentStatus
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.modules.ModuleManager
import java.lang.ref.WeakReference

class ConsentManagerWrapper(
    private val moduleManager: WeakReference<ModuleManager>
) : ConsentManager {
    private val delegate: ConsentManager?
        get() = TODO()//moduleManager.get()?.getModuleOfType(ConsentManager::class.java)

    override var consentStatus: ConsentStatus
        get() = delegate?.consentStatus ?: ConsentStatus.Unknown
        set(value) {
            delegate?.consentStatus = value
        }
}

class ConsentManagerImpl(
    private val context: TealiumContext
) : ConsentManager, Module {

    private var currentStatus: ConsentStatus = ConsentStatus.Unknown

    override var consentStatus: ConsentStatus
        get() = currentStatus
        set(value) {
            currentStatus = value
        }

    override val id: String
        get() = moduleName
    override val version: String
        get() = BuildConfig.TEALIUM_LIBRARY_VERSION


    companion object {
        private const val moduleName = "ConsentManager"
    }

    object Factory : ModuleFactory {
        override val id: String
            get() = moduleName

        override fun create(context: TealiumContext, configuration: DataObject): Module? {
            return ConsentManagerImpl(context)
        }
    }
}

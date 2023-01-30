package com.tealium.core.internal

import com.tealium.core.TealiumContext
import com.tealium.core.api.ConsentManager
import com.tealium.core.api.ConsentStatus
import com.tealium.core.api.Messenger
import com.tealium.core.api.Module
import com.tealium.core.api.listeners.ConsentStatusUpdatedListener
import com.tealium.core.internal.modules.ModuleManagerImpl
import java.lang.ref.WeakReference


class ConsentManagerWrapper(
    private val moduleManager: WeakReference<ModuleManagerImpl>
) : ConsentManager {
    private val delegate: ConsentManager?
        get() = moduleManager.get()?.getModuleOfType(ConsentManager::class.java)

    override var consentStatus: ConsentStatus
        get() = delegate?.consentStatus ?: ConsentStatus.Unknown
        set(value) {
            delegate?.consentStatus = value
        }
}

class ConsentManagerImpl(
    private val context: TealiumContext,
    private val onConsentStatusUpdatedDelegate: ConsentStatusUpdatedListener
) : ConsentManager, Module {

    constructor(context: TealiumContext, eventRouter: EventRouter) : this(
        context,
        object : ConsentStatusUpdatedListener {
            override fun onConsentStatusUpdated(status: ConsentStatus) {
                eventRouter.send(ConsentStatusUpdatedMessenger(status))
            }
        }
    )

    private var currentStatus: ConsentStatus = ConsentStatus.Unknown

    override var consentStatus: ConsentStatus
        get() = currentStatus
        set(value) {
            currentStatus = value
            onConsentStatusUpdatedDelegate.onConsentStatusUpdated(currentStatus)
        }

    override val name: String
        get() = moduleName
    override val version: String
        get() = "" //TODO("Not yet implemented")


    companion object {
        private const val moduleName = "ConsentManager"
    }

    private class ConsentStatusUpdatedMessenger(private val newStatus: ConsentStatus) :
        Messenger<ConsentStatusUpdatedListener>(ConsentStatusUpdatedListener::class) {
        override fun deliver(listener: ConsentStatusUpdatedListener) {
            listener.onConsentStatusUpdated(newStatus)
        }
    }
}

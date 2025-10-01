package com.tealium.prism.core.internal.modules.deeplink

import android.net.Uri
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.DeepLinkHandler
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.pubsub.Single

class DeepLinkHandlerWrapper(
    private val moduleProxy: ModuleProxy<DeepLinkModule>
) : DeepLinkHandler {
    constructor(
        tealium: Tealium
    ) : this(tealium.createModuleProxy(DeepLinkModule::class.java))

    override fun handle(link: Uri): Single<TealiumResult<Unit>> =
        handle(link, null)

    override fun handle(
        link: Uri,
        referrer: Uri?
    ): Single<TealiumResult<Unit>> =
        moduleProxy.executeModuleTask { deepLinkHandler ->
            deepLinkHandler.handle(link, referrer)
        }
}
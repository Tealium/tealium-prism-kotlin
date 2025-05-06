package com.tealium.core.internal.modules.deeplink

import android.net.Uri
import com.tealium.core.api.Tealium
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.modules.DeepLinkHandler
import com.tealium.core.api.modules.ModuleProxy
import com.tealium.core.api.pubsub.Single

class DeepLinkHandlerWrapper(
    private val moduleProxy: ModuleProxy<DeepLinkHandlerModule>
) : DeepLinkHandler {
    constructor(
        tealium: Tealium
    ) : this(tealium.createModuleProxy(DeepLinkHandlerModule::class.java))

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
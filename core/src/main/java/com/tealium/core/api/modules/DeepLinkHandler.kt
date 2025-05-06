package com.tealium.core.api.modules

import android.net.Uri
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.pubsub.Single


/**
 * The `DeepLinkHandler` is responsible for tracking incoming deep links, managing attribution, and
 * handling trace parameters when present in the URL.
 *
 * DeepLink handling is automatic called unless explicitly disabled.
 *
 */
interface DeepLinkHandler {

    /**
     * Handles a deep link for various purposes, such as attribution or trace management.
     *
     * @param link: The [Uri] representing the deep link to be handled.
     * @return: A [Single] containing a [TealiumResult] that indicates success ([Unit]) or failure ([Exception]).
     */
    fun handle(link: Uri): Single<TealiumResult<Unit>>

    /**
     * Handles a deep link for various purposes, such as attribution or trace management.
     *
     * @param link: The [Uri] representing the deep link to be handled.
     * @param referrer: An optional [Uri] indicating the source of the deep link.
     * @return: A [Single] containing a [TealiumResult] that indicates success ([Unit]) or failure ([Exception]).
     */
    fun handle(link: Uri, referrer: Uri?): Single<TealiumResult<Unit>>
}

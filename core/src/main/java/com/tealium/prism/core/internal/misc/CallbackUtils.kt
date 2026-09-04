package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult

/**
 * Runs [block] and completes this [Callback] exactly once: with a successful [TealiumResult] if
 * [block] returns, or with a failed one carrying whatever it threw, including an [Error].
 */
@JvmSynthetic
internal inline fun Callback<TealiumResult<Unit>>.completing(crossinline block: () -> Unit) {
    val result: TealiumResult<Unit> = try {
        block()
        TealiumResult.success(Unit)
    } catch (t: Throwable) {
        TealiumResult.failure(t)
    }

    onComplete(result)
}

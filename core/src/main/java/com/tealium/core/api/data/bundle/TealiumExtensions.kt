package com.tealium.core.api.data.bundle

fun tealiumBundleOf(
    vararg args: Pair<String, TealiumValue>
): TealiumBundle {
    return TealiumBundle.create {
        for (arg in args) {
            put(arg.first, arg.second)
        }
    }
}
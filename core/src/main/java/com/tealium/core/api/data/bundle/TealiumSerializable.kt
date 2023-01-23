package com.tealium.core.api.data.bundle

interface TealiumSerializable {
    fun serialize(): TealiumValue
}
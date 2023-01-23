package com.tealium.core.api.data.bundle

import com.tealium.core.api.Deserializer

interface TealiumDeserializable<T> : Deserializer<T?, TealiumValue> {
    override fun deserialize(value: TealiumValue): T?
}
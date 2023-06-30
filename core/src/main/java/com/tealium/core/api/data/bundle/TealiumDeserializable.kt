package com.tealium.core.api.data.bundle

import com.tealium.core.api.Deserializer

interface TealiumDeserializable<T> : Deserializer<TealiumValue, T?> {
    override fun deserialize(value: TealiumValue): T?
}
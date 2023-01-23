package com.tealium.core

import com.tealium.core.api.data.bundle.TealiumDeserializable
import com.tealium.core.api.data.bundle.TealiumSerializable
import com.tealium.core.api.data.bundle.TealiumValue

enum class Environment(val environment: String) : TealiumSerializable, TealiumDeserializable<Environment> {
    DEV("dev"),
    QA("qa"),
    PROD("prod");

    override fun serialize(): TealiumValue {
        return TealiumValue.string(this.environment)
    }

    override fun deserialize(value: TealiumValue): Environment? {
        return value.getString()?.let { str ->
            when (str) {
                DEV.environment -> DEV
                QA.environment -> QA
                PROD.environment -> PROD
                else -> null
            }
        }
    }
}
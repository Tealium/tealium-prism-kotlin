package com.tealium.core.api.misc

import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.data.TealiumValue

enum class Environment(val environment: String) : TealiumSerializable,
    TealiumDeserializable<Environment> {
    DEV("dev"),
    QA("qa"),
    PROD("prod");

    override fun asTealiumValue(): TealiumValue {
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
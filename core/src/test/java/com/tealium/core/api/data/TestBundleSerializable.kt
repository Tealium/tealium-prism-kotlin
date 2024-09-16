package com.tealium.core.api.data

data class TestBundleSerializable(
    val string: String,
    val int: Int
) : TealiumSerializable {

    override fun asTealiumValue(): TealiumValue {
        return TealiumBundle.create {
            put(Deserializer.KEY_STRING, string)
            put(Deserializer.KEY_INT, int)
        }.asTealiumValue()
    }

    object Deserializer : TealiumDeserializable<TestBundleSerializable> {
        const val KEY_STRING = "string"
        const val KEY_INT = "int"

        override fun deserialize(value: TealiumValue): TestBundleSerializable? {
            if (value.isBundle()) {
                value.getBundle()?.let { bundle ->
                    val str = bundle.getString(KEY_STRING)
                    val int = bundle.getInt(KEY_INT)

                    if (str == null || int == null)
                        throw IllegalStateException()

                    return TestBundleSerializable(str, int)
                }
            }

            return null
        }
    }
}
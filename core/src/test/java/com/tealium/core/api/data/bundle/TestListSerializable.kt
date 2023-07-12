package com.tealium.core.api.data.bundle

data class TestListSerializable(
    private val string: String,
    private val int: Int
) : TealiumSerializable {

    override fun asTealiumValue(): TealiumValue {
        return TealiumList.create {
            add(string)
            add(int)
        }.asTealiumValue()
    }

    object Deserializer : TealiumDeserializable<TestListSerializable> {
        override fun deserialize(value: TealiumValue): TestListSerializable? {
            if (value.isList()) {
                value.getList()?.let { list ->
                    val str = list.getString(0)
                    val int = list.getInt(1)

                    if (str == null || int == null)
                        throw IllegalStateException()

                    return TestListSerializable(str, int)
                }
            }

            return null
        }
    }
}
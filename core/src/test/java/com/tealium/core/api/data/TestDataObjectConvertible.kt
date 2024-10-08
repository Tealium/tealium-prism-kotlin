package com.tealium.core.api.data

data class TestDataObjectConvertible(
    val string: String,
    val int: Int
) : DataItemConvertible {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(Converter.KEY_STRING, string)
            put(Converter.KEY_INT, int)
        }.asDataItem()
    }

    object Converter : DataItemConverter<TestDataObjectConvertible> {
        const val KEY_STRING = "string"
        const val KEY_INT = "int"

        override fun convert(dataItem: DataItem): TestDataObjectConvertible? {
            if (dataItem.isDataObject()) {
                dataItem.getDataObject()?.let { dataObject ->
                    val str = dataObject.getString(KEY_STRING)
                    val int = dataObject.getInt(KEY_INT)

                    if (str == null || int == null)
                        throw IllegalStateException()

                    return TestDataObjectConvertible(str, int)
                }
            }

            return null
        }
    }
}
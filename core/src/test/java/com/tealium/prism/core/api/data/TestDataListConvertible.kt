package com.tealium.prism.core.api.data

data class TestDataListConvertible(
    val string: String,
    val int: Int
) : DataItemConvertible {

    override fun asDataItem(): DataItem {
        return DataList.create {
            add(string)
            add(int)
        }.asDataItem()
    }

    object Converter : DataItemConverter<TestDataListConvertible> {
        override fun convert(dataItem: DataItem): TestDataListConvertible? {
            if (dataItem.isDataList()) {
                dataItem.getDataList()?.let { list ->
                    val str = list.getString(0)
                    val int = list.getInt(1)

                    if (str == null || int == null)
                        throw IllegalStateException()

                    return TestDataListConvertible(str, int)
                }
            }

            return null
        }
    }
}
package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConvertible

/**
 * Represents the available regions for the MomentsApi.
 * 
 * The region determines which Tealium AudienceStream instance the API calls are made against.
 * Use [Custom] to support future regions that may be released without requiring an SDK update.
 */
sealed class MomentsApiRegion(val value: String) : DataItemConvertible {
    object Germany : MomentsApiRegion("eu-central-1")
    object UsEast : MomentsApiRegion("us-east-1")
    object Sydney : MomentsApiRegion("ap-southeast-2")
    object Oregon : MomentsApiRegion("us-west-2")
    object Tokyo : MomentsApiRegion("ap-northeast-1")
    object HongKong : MomentsApiRegion("ap-east-1")
    data class Custom(private val region: String) : MomentsApiRegion(region)

    override fun asDataItem(): DataItem {
        return DataItem.string(value)
    }
}

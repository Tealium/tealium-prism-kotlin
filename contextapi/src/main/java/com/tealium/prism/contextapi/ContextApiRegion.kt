package com.tealium.prism.contextapi

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConvertible

/**
 * Represents the available regions for the ContextApi.
 * 
 * The region determines which Tealium AudienceStream instance the API calls are made against.
 * Use [Custom] to support future regions that may be released without requiring an SDK update.
 */
sealed class ContextApiRegion(val value: String) : DataItemConvertible {
    object Germany : ContextApiRegion("eu-central-1")
    object UsEast : ContextApiRegion("us-east-1")
    object Sydney : ContextApiRegion("ap-southeast-2")
    object Oregon : ContextApiRegion("us-west-2")
    object Tokyo : ContextApiRegion("ap-northeast-1")
    object HongKong : ContextApiRegion("ap-east-1")
    data class Custom(private val region: String) : ContextApiRegion(region)

    override fun asDataItem(): DataItem {
        return DataItem.string(value)
    }
}

package com.tealium.prism.core.api.settings.barriers

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.internal.settings.BarrierSettings

/**
 * A builder class for configuring barrier settings.
 */
open class BarrierSettingsBuilder<T : BarrierSettingsBuilder<T>> {
    private val builder = DataObject.Builder()
    
    /**
     * A custom object that holds the configuration for this barrier.
     * Do not use this one directly unless you are subclassing this class.
     */
    protected val configurationBuilder = DataObject.Builder()

    /**
     * Set the scopes where this barrier should be applied.
     * 
     * @param scopes A set of [BarrierScope] values defining where the barrier applies.
     * @return The builder instance for method chaining.
     */
    @Suppress("UNCHECKED_CAST")
    fun setScopes(scopes: Set<BarrierScope>): T = apply {
        builder.put(BarrierSettings.Converter.KEY_SCOPES, DataItem.convert(scopes))
    } as T

    /**
     * @return the [DataObject] representing the [BarrierSettings] object.
     */
    fun build(): DataObject {
        val config = configurationBuilder.build()
        builder.put(BarrierSettings.Converter.KEY_CONFIGURATION, config)
        return builder.build()
    }
}

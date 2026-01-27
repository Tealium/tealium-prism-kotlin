package com.tealium.prism.jstransformer

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.jstransformer.internal.JavaScriptTransformer

val Modules.Types.JS_TRANSFORMER
    get() = JavaScriptTransformerFactory.MODULE_TYPE

abstract class JavaScriptTransformerFactory(
    private val adapterProvider: (TealiumContext) -> JavaScriptEngineAdapter
) : ModuleFactory {

    override fun canBeDisabled(): Boolean = false

    override fun getEnforcedSettings(): List<DataObject> {
        return listOf(ModuleSettingsBuilder(moduleType).build())
    }

    override val moduleType: String
        get() = MODULE_TYPE

    override fun create(
        moduleId: String,
        context: TealiumContext,
        configuration: DataObject
    ): Module? {
        val adapter = try {
            adapterProvider.invoke(context)
        } catch (e: Exception) {
            context.logger.warn(moduleType, "Error initializing JavaScriptEngineAdapter: ${e.message}")
            return null
        }

        return JavaScriptTransformer(adapter, context.logger)
    }

    companion object {
        const val MODULE_TYPE = "JavaScriptTransformer"
    }
}
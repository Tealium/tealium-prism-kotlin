package com.tealium.prism.jstransformer

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.settings.modules.ModuleSettingsBuilder
import com.tealium.prism.jstransformer.internal.JavaScriptTransformer

/**
 * Module type identifier for the JavaScript Transformer module.
 */
val Modules.Types.JS_TRANSFORMER
    get() = JavaScriptTransformerFactory.MODULE_TYPE

/**
 * Factory responsible for creating and configuring the [JavaScriptTransformer] module.
 *
 * Subclasses must provide an [adapterProvider] that supplies a [JavaScriptEngineAdapter]
 * for the given [TealiumContext]. If the adapter fails to initialize, the factory logs a
 * warning and returns null, preventing the module from being registered.
 *
 * This factory is always enforced and cannot be disabled.
 */
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
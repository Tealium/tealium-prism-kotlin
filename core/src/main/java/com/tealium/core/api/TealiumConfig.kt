package com.tealium.core.api

import android.app.Application
import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.barriers.BarrierFactory
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.logger.LogHandler
import com.tealium.core.api.misc.Environment
import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.modules.Module
import com.tealium.core.api.modules.ModuleFactory
import com.tealium.core.api.rules.Condition
import com.tealium.core.api.rules.Rule
import com.tealium.core.api.settings.CoreSettingsBuilder
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.api.transform.Transformer
import com.tealium.core.internal.logger.LoggerImpl
import com.tealium.core.internal.rules.LoadRule
import com.tealium.core.internal.settings.BarrierSettings
import com.tealium.core.internal.settings.CoreSettingsImpl
import com.tealium.core.internal.settings.SdkSettings
import java.io.File

class TealiumConfig @JvmOverloads constructor(
    val application: Application,
    val accountName: String,
    val profileName: String,
    val environment: Environment,
    val modules: List<ModuleFactory>,
    val datasource: String? = null,
    enforcingCoreSettings: ((CoreSettingsBuilder) -> CoreSettingsBuilder)? = null
) {
    val key: String = "${accountName}-${profileName}"
    private val coreSettings = enforcingCoreSettings?.invoke(CoreSettingsBuilder())?.build()

    // TODO - These are mutable, as are other properties - need to add defensive copy functionality
    private val rules = DataObject.Builder()
    private val transformations = DataObject.Builder()
    private val barrierSettings = DataObject.Builder()

    private val pathName
        get() = "${application.filesDir}${File.separatorChar}tealium${File.separatorChar}${accountName}${File.separatorChar}${profileName}${File.separatorChar}${environment.environment}"
    val tealiumDirectory: File
        get() = File(pathName)

    var useRemoteSettings: Boolean = false

    /**
     * File name for local settings
     */
    var localSdkSettingsFileName: String? = null

    /**
     * Overrides remote URL to use when fetching remote settings
     */
    var sdkSettingsUrl: String? = null

    /**
     * Overrides default LogHandler. Default will log to the Android console.
     */
    var logHandler: LogHandler = LoggerImpl.ConsoleLogHandler

    /**
     * Sets a known existing visitor id for use only on first launch.
     */
    var existingVisitorId: String? = null

    /**
     * Sets a load rule to be used by any of the modules by the rule's id.
     *
     * @param id The id used to look up this specific rule when defining it in the [Module]'s Settings
     * @param rule The [Rule<Condition>] that defines when that rule should match a payload.
     */
    fun addLoadRule(id: String, rule: Rule<Condition>) {
        rules.put(id, LoadRule(id, rule))
    }

    /**
     * Sets a transformation to be used by a specific transformer.
     *
     * The transformation Id and transformer Id will be combined and need to be unique or the newer
     * transformation will replace older ones.
     *
     * @param transformation The [TransformationSettings] that defines which [Transformer] should
     * handle this transformation and how.
     */
    fun addTransformation(transformation: TransformationSettings) {
        transformations.put("${transformation.transformerId}-${transformation.id}", transformation)
    }

    var barriers: List<BarrierFactory> = emptyList()
        private set

    /**
     * Registers a `barrier` that is able to control the flow of events to [Dispatcher] instances
     * via setting of [scopes].
     *
     * Setting the [scopes] will override any settings from remote/local sources. Leaving it as `null`
     * will use any settings from remote or local sources, or a default if the [barrier] supports this.
     *
     * @param barrier the [BarrierFactory] used to create the [Barrier] instance
     * @param scopes optionally fix the set of [Dispatcher]s that this barrier applies to.
     */
    @JvmOverloads
    fun addBarrier(barrier: BarrierFactory, scopes: Set<BarrierScope>? = null) {
        barriers = barriers + barrier
        if (scopes != null)
            barrierSettings.put(barrier.id, BarrierSettings(barrier.id, scopes))
    }

    val enforcedSdkSettings: DataObject
        get() = DataObject.create {
            if (coreSettings != null) {
                put(CoreSettingsImpl.MODULE_NAME, coreSettings)
            }
            val modulesObject = DataObject.create {
                modules.forEach { factory ->
                    factory.getEnforcedSettings()?.let { enforcedSettings ->
                        put(factory.id, enforcedSettings)
                    }
                }
            }
            put(SdkSettings.KEY_MODULES, modulesObject)

            val rules = rules.build()
            if (rules != DataObject.EMPTY_OBJECT) {
                put(SdkSettings.KEY_LOAD_RULES, rules)
            }

            val transformations = transformations.build()
            if (transformations != DataObject.EMPTY_OBJECT) {
                put(SdkSettings.KEY_TRANSFORMATIONS, transformations)
            }

            val barriers = barrierSettings.build()
            if (barriers != DataObject.EMPTY_OBJECT) {
                put(SdkSettings.KEY_BARRIERS, barriers)
            }
        }
}
package com.tealium.prism.core.api

import android.app.Application
import com.tealium.prism.core.api.barriers.Barrier
import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.ConfigurableBarrier
import com.tealium.prism.core.api.consent.CmpAdapter
import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.LogHandler
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.core.api.rules.Condition
import com.tealium.prism.core.api.rules.Rule
import com.tealium.prism.core.api.settings.ConsentConfigurationBuilder
import com.tealium.prism.core.api.settings.CoreSettingsBuilder
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.core.internal.logger.LoggerImpl
import com.tealium.prism.core.internal.rules.LoadRule
import com.tealium.prism.core.internal.settings.BarrierSettings
import com.tealium.prism.core.internal.settings.CoreSettingsImpl
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.prism.core.internal.settings.consent.ConsentSettings
import java.io.File

/**
 * The object used to configure [Tealium] instances with constant values and dependencies.
 *
 * @param application The android [Application] object
 * @param accountName The Tealium account name
 * @param profileName The Tealium profile name
 * @param environment The Tealium environment
 * @param modules A list of unique [ModuleFactory]'s each one for creating a specific [Module]
 * @param barriers A list of unique [BarrierFactory]s each one for creating a specific [ConfigurableBarrier]
 * @param datasource The datasource to identify the data coming from this SDK
 * @param settingsFile The name of the file in the Assets folder that contains local JSON Settings. These settings will be deep merged with any Remote and Programmatic settings, which will take priority over Local settings
 * @param settingsUrl The url to download Remote JSON Settings from. These settings will be deep merged with any Local and Programmatic settings. Remote settings will take priority over Local ones, and Programmatic settings will take priority over Remote settings
 * @param logHandler The [LogHandler] to delegate logging events to; default will write to [android.util.Log]
 * @param existingVisitorId A known existing visitor id to use on first launch instead of our anonymous id
 * @param cmpAdapter An adapter that can convert CMP specific data to a [ConsentDecision] that the [Tealium] consent integration system can handle.
 * @param enforcedSdkSettings A [DataObject] containing all the settings that were configured programmatically
 */
class TealiumConfig private constructor(
    val application: Application,
    val accountName: String,
    val profileName: String,
    val environment: String,
    val modules: List<ModuleFactory>,
    val barriers: List<BarrierFactory>,
    val datasource: String?,
    val settingsFile: String?,
    val settingsUrl: String?,
    val logHandler: LogHandler,
    val existingVisitorId: String?,
    val cmpAdapter: CmpAdapter?,
    val enforcedSdkSettings: DataObject
) {

    /**
     * A key used to uniquely identify [Tealium] instances.
     */
    val key: String = "${accountName}-${profileName}"

    private val pathName
        get() = listOf(
            application.filesDir,
            "tealium-prism",
            accountName,
            profileName
        ).joinToString(File.separator)

    val tealiumDirectory: File
        get() = File(pathName)

    /**
     * A builder class for configuring a [Tealium] instance.
     *
     * @param application The android [Application] object
     * @param accountName The Tealium account name
     * @param profileName The Tealium profile name
     * @param environment The Tealium environment
     * @param modules A list of unique [ModuleFactory]'s each one for creating a specific [Module]
     */
    class Builder(
        val application: Application,
        val accountName: String,
        val profileName: String,
        val environment: String,
        modules: List<ModuleFactory>
    ) {
        private val modules = modules.toMutableList()
        private val rules = DataObject.Builder()
        private val transformations = DataObject.Builder()
        private val barrierSettings = DataObject.Builder()
        private val consentSettings = DataObject.Builder()
        private val barriers: MutableList<BarrierFactory> = mutableListOf()
        private var datasource: String? = null
        private var enforcingCoreSettings: ((CoreSettingsBuilder) -> CoreSettingsBuilder)? = null
        private var settingsFile: String? = null
        private var settingsUrl: String? = null
        private var logHandler: LogHandler = LoggerImpl.ConsoleLogHandler
        private var existingVisitorId: String? = null
        private var cmpAdapter: CmpAdapter? = null

        /**
         * Sets the data source id used to identify data coming from this SDK.
         */
        fun setDataSource(datasource: String) = apply {
            this.datasource = datasource
        }

        /**
         * The name of the file in the Assets folder that contains local JSON Settings.
         *
         * These settings will be deep merged with any Remote and Programmatic settings, which will
         * take priority over Local settings
         */
        fun setSettingsFile(filename: String) = apply {
            this.settingsFile = filename
        }

        /**
         * The url to download Remote JSON Settings from.
         *
         * These settings will be deep merged with any Local and Programmatic settings.
         *
         * Remote settings will take priority over Local ones, and Programmatic settings will take
         * priority over Remote settings
         */
        fun setSettingsUrl(url: String) = apply {
            this.settingsUrl = url
        }

        /**
         * The [LogHandler] to delegate logging events to; default will write to [android.util.Log]
         */
        fun setLogHandler(logHandler: LogHandler) = apply {
            this.logHandler = logHandler
        }

        /**
         * A known existing visitor id to use on first launch instead of our anonymous id
         */
        fun setExistingVisitorId(existingVisitorId: String) = apply {
            this.existingVisitorId = existingVisitorId
        }

        /**
         * Configures core settings related to the SDK.
         */
        fun configureCoreSettings(
            enforcingCoreSettings: ((CoreSettingsBuilder) -> CoreSettingsBuilder)
        ) = apply {
            this.enforcingCoreSettings = enforcingCoreSettings
        }

        /**
         * Adds a [ModuleFactory] to the list of modules that need to be instantiated by the SDK.
         *
         * You can add a [ModuleFactory] for a specific [Module] only once. Adding two of them will
         * result in all but the first to be discarded. Some specific [ModuleFactory], like [Modules.collect]
         * can potentially instantiate more than one Collect module, if they are provided with multiple
         * settings and different [Module] ID's, but the factory still only needs to be added once.
         *
         * @param module The unique [ModuleFactory] used to create a specific type of [Module].
         */
        fun addModule(module: ModuleFactory) = apply {
            modules.add(module)
        }

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
        fun addBarrier(barrier: BarrierFactory, scopes: Set<BarrierScope>? = null) = apply {
            barriers.add(barrier)
            if (scopes != null)
                barrierSettings.put(barrier.id, BarrierSettings(barrier.id, scopes))
        }

        /**
         * Enable consent integration with a [CmpAdapter].
         *
         * If you enable consent integration events will only be tracked after the [CmpAdapter] returns a [ConsentDecision],
         * And only after a Consent Configuration is found for that adapter.
         *
         * Make sure to properly configure consent either locally, remotely or programmatically
         * for the provided [CmpAdapter] to ensure proper tracking.
         *
         * @param cmpAdapter: The adapter that will report the [ConsentDecision] to the SDK
         * @param enforcingSettings: An optional block called with a configuration builder, used to force some of the Consent Configuration properties.
         *          Properties set with this block will have precedence to local and remote settings.
         */
        @JvmOverloads
        fun enableConsentIntegration(
            cmpAdapter: CmpAdapter,
            enforcingSettings: ((ConsentConfigurationBuilder) -> ConsentConfigurationBuilder)? = null
        ) = apply {
            this.cmpAdapter = cmpAdapter
            consentSettings.clear()
            if (enforcingSettings == null) return@apply

            val consentConfiguration = enforcingSettings.invoke(ConsentConfigurationBuilder())
                .build()

            consentSettings.put(ConsentSettings.Converter.KEY_CONFIGURATIONS, DataObject.create {
                put(cmpAdapter.id, consentConfiguration)
            })
        }

        /**
         * Sets a load rule to be used by any of the modules by the rule's id.
         *
         * @param id The id used to look up this specific rule when defining it in the [Module]'s Settings
         * @param rule The [Rule<Condition>] that defines when that rule should match a payload.
         */
        fun addLoadRule(id: String, rule: Rule<Condition>) = apply {
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
        fun addTransformation(transformation: TransformationSettings) = apply {
            transformations.put(
                "${transformation.transformerId}-${transformation.id}",
                transformation
            )
        }

        private fun enforcedSdkSettings(deduplicatedModules: List<ModuleFactory>): DataObject =
            DataObject.create {
                val coreSettings = enforcingCoreSettings?.invoke(CoreSettingsBuilder())
                if (coreSettings != null) {
                    put(CoreSettingsImpl.MODULE_NAME, coreSettings.build())
                }

                val modulesObject = DataObject.create {
                    deduplicatedModules.flatMap { factory ->
                        factory.getEnforcedSettings().map { settings ->
                            val moduleId = settings.getString(ModuleSettings.KEY_MODULE_ID)
                                ?: factory.moduleType

                            moduleId to settings.copy {
                                put(ModuleSettings.KEY_MODULE_TYPE, factory.moduleType)
                            }
                        }
                    }.distinctBy { (moduleId, _) -> moduleId }
                        .forEach { (moduleId, enforcedSettings) -> put(moduleId, enforcedSettings) }
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

                val consent = consentSettings.build()
                if (consent != DataObject.EMPTY_OBJECT) {
                    put(SdkSettings.KEY_CONSENT, consent)
                }
            }

        /**
         * Takes an immutable copy of any configured settings
         */
        fun build(): TealiumConfig {
            // Add defaults and dedupe
            val dedupedModules = (modules + Modules.defaultModules)
                .distinctBy(ModuleFactory::moduleType)

            return TealiumConfig(
                application,
                accountName,
                profileName,
                environment,
                dedupedModules,
                barriers.toList(),
                datasource,
                settingsFile,
                settingsUrl,
                logHandler,
                existingVisitorId,
                cmpAdapter,
                enforcedSdkSettings(dedupedModules)
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TealiumConfig

        if (application != other.application) return false
        if (accountName != other.accountName) return false
        if (profileName != other.profileName) return false
        if (environment != other.environment) return false
        if (modules != other.modules) return false
        if (barriers != other.barriers) return false
        if (datasource != other.datasource) return false
        if (settingsFile != other.settingsFile) return false
        if (settingsUrl != other.settingsUrl) return false
        if (logHandler != other.logHandler) return false
        if (existingVisitorId != other.existingVisitorId) return false
        if (cmpAdapter != other.cmpAdapter) return false
        if (enforcedSdkSettings != other.enforcedSdkSettings) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = application.hashCode()
        result = 31 * result + accountName.hashCode()
        result = 31 * result + profileName.hashCode()
        result = 31 * result + environment.hashCode()
        result = 31 * result + modules.hashCode()
        result = 31 * result + barriers.hashCode()
        result = 31 * result + (datasource?.hashCode() ?: 0)
        result = 31 * result + (settingsFile?.hashCode() ?: 0)
        result = 31 * result + (settingsUrl?.hashCode() ?: 0)
        result = 31 * result + logHandler.hashCode()
        result = 31 * result + (existingVisitorId?.hashCode() ?: 0)
        result = 31 * result + (cmpAdapter?.hashCode() ?: 0)
        result = 31 * result + enforcedSdkSettings.hashCode()
        result = 31 * result + key.hashCode()
        return result
    }
}
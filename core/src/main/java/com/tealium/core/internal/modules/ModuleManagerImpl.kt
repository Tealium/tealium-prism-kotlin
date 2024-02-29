package com.tealium.core.internal.modules

import com.tealium.core.TealiumContext
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.internal.settings.ModuleSettingsImpl
import com.tealium.core.api.settings.SettingsProvider
import com.tealium.core.internal.SdkSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock

class ModuleManagerImpl(
    private val context: TealiumContext,
    initialSdkSettings: SdkSettings,
    private val moduleFactories: List<ModuleFactory>,
    private val settingsProvider: SettingsProvider,
    private val tealiumScope: CoroutineScope
) : ModuleManager {

    private val updateLock = ReentrantReadWriteLock()
    private val readLock = updateLock.readLock()
    private val writeLock = updateLock.writeLock()

    private var modules: Map<String, Module> =
        createModules(context, initialSdkSettings.moduleSettings, moduleFactories)

    init {
        logEnabledModules()
    }

    override fun <T> getModulesOfType(clazz: Class<T>): List<T> {
        lock(readLock)
        return try {
            modules.values.filterIsInstance(clazz)
        } finally {
            unlock(readLock)
        }
    }

    override fun <T> getModuleOfType(clazz: Class<T>): T? {
        return getModulesOfType(clazz).firstOrNull()
    }

    override fun <T> getModuleOfType(clazz: Class<T>, block: (T?) -> Unit) {
        tealiumScope.launch {
            block.invoke(getModuleOfType(clazz))
        }
    }

    init {
        settingsProvider.onSdkSettingsUpdated
            .subscribe(::updateModuleSettings)
    }

    private fun updateModuleSettings(settings: SdkSettings) {
        lock(writeLock)

        try {
            // iterate all factories to preserve insertion order
            modules = moduleFactories.mapNotNull { factory ->
                val module = modules[factory.name]?.let { module ->
                    // update all existing module settings in case disabled modules need to shut
                    // anything down
                    val updatedModuleSettings = settings.moduleSettings.getOrDefault(factory.name)
                    module.updateSettings(updatedModuleSettings)

                    if (updatedModuleSettings.enabled) module else null
                } ?: run {
                    // module was previously disabled, needs creating.
                    createModule(
                        context,
                        settings.moduleSettings.getOrDefault(factory.name),
                        factory
                    )
                }

                if (module != null)
                    factory.name to module
                else null
            }.toMap()

            logEnabledModules()
        } finally {
            unlock(writeLock)
        }
    }

    private fun logEnabledModules() {
        context.logger.info?.log(
            "ModuleManager",
            "Enabled modules: [${modules.keys.joinToString(", ")}]"
        )
    }

    /**
     * Locks a Lock implementation.
     */
    private fun lock(lock: Lock) {
        lock.lock()
    }

    /**
     * Unlocks a Lock implementation, discarding any exceptions
     */
    private fun unlock(lock: Lock) {
        try {
            lock.unlock()
        } catch (ex: IllegalMonitorStateException) {
            context.logger.error?.log("Modules", "Unlocking Thread was not the lock owner.")
        } catch (ex: Exception) {
            context.logger.error?.log("Modules", "Unlocking failed: ${ex.message}")
        }
    }

    companion object {
        /**
         * Instantiates modules from their factories and associates them with the factory name.
         *
         * If a module is disabled according to the settings, then it will not be created.
         */
        private fun createModules(
            context: TealiumContext,
            modulesConfigs: Map<String, ModuleSettings>,
            factories: List<ModuleFactory>
        ): Map<String, Module> {
            return factories.mapNotNull {
                createModule(context, modulesConfigs.getOrDefault(it.name), it)
            }.associateBy { it.name }
        }

        private fun createModule(
            context: TealiumContext,
            settings: ModuleSettings,
            factory: ModuleFactory
        ): Module? {
            return if (settings.enabled) {
                factory.create(context, settings)
            } else null
        }

        /**
         * Extracts the module settings for the named module; otherwise returns the default settings.
         */
        private fun getModuleSettings(
            moduleName: String,
            modulesSettings: Map<String, ModuleSettings>
        ): ModuleSettings {
            return modulesSettings[moduleName] ?: ModuleSettingsImpl()
        }

        /**
         * Extracts the module settings for the named module; otherwise returns the default settings.
         */
        private fun Map<String, ModuleSettings>.getOrDefault(name: String): ModuleSettings {
            return getModuleSettings(name, this)
        }
    }
}


package com.tealium.core.internal.modules

import android.util.Log
import com.tealium.core.TealiumContext
import com.tealium.core.api.CoreSettings
import com.tealium.core.api.Module
import com.tealium.core.api.ModuleFactory
import com.tealium.core.api.ModuleManager
import com.tealium.core.api.ModuleSettings
import com.tealium.core.api.listeners.SettingsUpdatedListener
import com.tealium.core.internal.SdkSettings
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock

class ModuleManagerImpl(
    private val context: TealiumContext,
    initialSettings: SdkSettings,
    private val moduleFactories: List<ModuleFactory>,
) : ModuleManager, SettingsUpdatedListener {

    private val updateLock = ReentrantReadWriteLock()
    private val readLock = updateLock.readLock()
    private val writeLock = updateLock.writeLock()

    private var modules: Map<String, Module> =
        createModules(context, initialSettings.moduleSettings, moduleFactories)

    override fun <T> getModulesOfType(clazz: Class<T>): List<T> {
        lock(readLock)
        return try {
            modules.values.filterIsInstance(clazz)
        } finally {
            unlock(readLock)
        }
    }

    init {
        logEnabledModules()
    }

    override fun <T> getModuleOfType(clazz: Class<T>): T? {
        return getModulesOfType(clazz).first()
    }

    override fun onSettingsUpdated(
        coreSettings: CoreSettings,
        moduleSettings: Map<String, ModuleSettings>
    ) {
        // existing modules will have their settings updated before the [modules] list is updated.
        // other threads should not be getting existing modules before updates are completed.
        lock(writeLock)

        try {
            // iterate all factories to preserve insertion order
            modules = moduleFactories.mapNotNull { factory ->
                val module = modules[factory.name]?.let { module ->
                    // update all existing module settings in case disabled modules need to shut
                    // anything down
                    val updatedModuleSettings = moduleSettings.getOrDefault(factory.name)
                    module.updateSettings(coreSettings, updatedModuleSettings)

                    if (updatedModuleSettings.enabled) module else null
                } ?: run {
                    // module was previously disabled, needs creating.
                    createModule(context, moduleSettings.getOrDefault(factory.name), factory)
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
        context.logger.info("ModuleManager", "Enabled modules: [${modules.keys.joinToString(", ")}]")
    }

    companion object {

        /**
         * Instantiates modules from their factories and associates them with the factory name.
         *
         * If a module is disabled according to the settings, then it will not be created.
         */
        private fun createModules(
            context: TealiumContext,
            modulesSettings: Map<String, ModuleSettings>,
            factories: List<ModuleFactory>
        ): Map<String, Module> {
            return factories.mapNotNull {
                createModule(context, modulesSettings.getOrDefault(it.name), it)
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
                Log.d("Modules", "Unlocking Thread was not the lock owner.")
            } catch (ex: Exception) {
                Log.d("Modules", "Unlocking failed: ${ex.message}")
            }
        }
    }
}


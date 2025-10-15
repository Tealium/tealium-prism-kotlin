package com.tealium.prism.core.internal.modules

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import com.tealium.prism.core.api.modules.ModuleFactory

/**
 * Non-operational service to allow external modules to register their [ModuleFactory] details with.
 *
 * External modules should declare the [ModuleDiscoveryService] in their AndroidManifest.xml as so:
 *
 * ```xml
 * <application>
 *     <service android:name="com.tealium.prism.core.internal.modules.ModuleDiscoveryService"
 *         android:exported="false">
 *         <meta-data
 *             android:name="com.tealium.prism.core.modules:<fully qualified class name>"
 *             android:value="com.tealium.prism.core.api.ModuleFactory" />
 *     </service>
 * </application>
 * ```
 *
 * Manifest merge rules is expected to combine declarations into a single <service> block containing
 * all external modules. The `<meta-data ..>` blocks can then be used to reflectively look up
 * external factories.
 *
 * Formats supported for the class name:
 *  - `com.<company>.<class>`
 *     - This will attempt to create the class instance through a no-args constructor.
 *     - failing that, it will attempt to read it as a Kotlin `object` `INSTANCE` field
 *     - failing that, it will attempt to read it as a Kotlin `companion object`
 * - `com.<company>.<class>#<field_name>`
 *     - This will attempt to read the `<field_name>` as a `static` field of the given class name.
 *
 * Note. Since the class names are looked up reflectively, the external module developer should ensure
 * that appropriate pro-guard and consumer pro-guard rules are in place to keep the class will be
 * looked up.
 */
class ModuleDiscoveryService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        // no binding allowed
        return null
    }

    companion object {
        const val METADATA_PREFIX = "com.tealium.prism.core.modules:"

        /**
         * Discovers all [ModuleFactory] implementations that are declared in external artifacts.
         */
        fun discover(context: Context): List<ModuleFactory> {
            val packageManager = context.applicationContext.packageManager
                ?: return emptyList()

            val serviceInfo = packageManager.getServiceInfo(
                ComponentName(context, ModuleDiscoveryService::class.java.name),
                PackageManager.GET_META_DATA
            )

            val metaData = serviceInfo.metaData
                ?: return emptyList()

            return metaData.keySet()
                .filter { it.startsWith(METADATA_PREFIX) }
                .mapNotNull { key ->
                    val clazz = key.substringAfter(METADATA_PREFIX)
                    loadFactory(clazz)
                }
        }

        private fun loadFactory(name: String): ModuleFactory? =
            try {
                if (name.contains("#")) {
                    val parts = name.split("#")
                    val clazz = Class.forName(parts[0])
                    loadFromField(clazz, parts[1])
                } else {
                    loadFromClass(Class.forName(name))
                }
            } catch (ignore: Exception) {
                null
            }

        private fun loadFromClass(clazz: Class<*>): ModuleFactory? {
            // try no-args constructor
            try {
                return clazz.getConstructor().newInstance() as ModuleFactory
            } catch (ignore: Exception) {
            }

            // try Kotlin `object`
            val objectInstance = loadFromField(clazz, "INSTANCE")
            if (objectInstance != null) return objectInstance

            // try Kotlin `Companion` object
            val companionInstance = loadFromField(clazz, "Companion")
            if (companionInstance != null) return companionInstance

            return null
        }

        private fun loadFromField(clazz: Class<*>, field: String): ModuleFactory? =
            try {
                clazz.getDeclaredField(field).get(clazz) as ModuleFactory
            } catch (e: Exception) {
                null
            }
    }
}
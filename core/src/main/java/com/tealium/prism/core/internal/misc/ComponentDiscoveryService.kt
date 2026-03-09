package com.tealium.prism.core.internal.misc

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.api.modules.ModuleFactory

/**
 * Non-operational service to allow external packages to register their [ModuleFactory] and [BarrierFactory]
 * details with.
 *
 * External packages should declare the [ComponentDiscoveryService] in their AndroidManifest.xml as so:
 *
 * ```xml
 * <application>
 *     <service android:name="com.tealium.prism.core.internal.misc.ComponentDiscoveryService"
 *         android:exported="false">
 *         <!-- Example ModuleFactory -->
 *         <meta-data
 *             android:name="com.tealium.prism.core.api.modules:<fully qualified class name>"
 *             android:value="com.tealium.prism.core.api.modules.ModuleFactory" />
 *         <!-- Example BarrierFactory -->
 *         <meta-data
 *             android:name="com.tealium.prism.core.api.barriers:<fully qualified class name>"
 *             android:value="com.tealium.prism.core.api.barriers.BarrierFactory" />
 *     </service>
 * </application>
 * ```
 *
 * Manifest merge rules is expected to combine declarations into a single <service> block containing
 * all components from external packages. The `<meta-data ..>` blocks can then be used to reflectively look up
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
 * Note. Since the class names are looked up reflectively, the external package developer should ensure
 * that appropriate pro-guard and consumer pro-guard rules are in place to keep the class that will be
 * looked up.
 */
class ComponentDiscoveryService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        // no binding allowed
        return null
    }

    companion object Companion {
        const val MODULES_METADATA_PREFIX = "com.tealium.prism.core.api.modules:"
        const val BARRIERS_METADATA_PREFIX = "com.tealium.prism.core.api.barriers:"

        /**
         * Utility method to discover all instances of [ModuleFactory] that have been declared in
         * the AndroidManifest files.
         */
        fun discoverModules(context: Context) =
            discover(context, MODULES_METADATA_PREFIX, ModuleFactory::class.java)

        /**
         * Utility method to discover all instances of [BarrierFactory] that have been declared in
         * the AndroidManifest files.
         */
        fun discoverBarriers(context: Context) =
            discover(context, BARRIERS_METADATA_PREFIX, BarrierFactory::class.java)

        /**
         * Discovers all implementations of the given [clazz] that are declared in external artifacts.
         */
        fun <T> discover(
            context: Context,
            metaDataPrefix: String,
            clazz: Class<T>
        ): List<T> {
            val packageManager = context.applicationContext.packageManager
                ?: return emptyList()

            val serviceInfo = packageManager.getServiceInfo(
                ComponentName(context, ComponentDiscoveryService::class.java.name),
                PackageManager.GET_META_DATA
            )

            val metaData = serviceInfo.metaData
                ?: return emptyList()

            return metaData.keySet()
                .filter { it.startsWith(metaDataPrefix) }
                .mapNotNull { key ->
                    val className = key.substringAfter(metaDataPrefix)
                    loadComponent(className, clazz)
                }
        }

        private fun <T> loadComponent(name: String, returnClass: Class<T>): T? =
            try {
                if (name.contains("#")) {
                    val parts = name.split("#")
                    val clazz = Class.forName(parts[0])
                    loadFromField(clazz, parts[1], returnClass)
                } else {
                    loadFromClass(Class.forName(name), returnClass)
                }
            } catch (_: Exception) {
                null
            }

        private fun <T> loadFromClass(clazz: Class<*>, returnClass: Class<T>): T? {
            // try no-args constructor
            try {
                val instance = clazz.getConstructor().newInstance()
                return returnClass.cast(instance)
            } catch (_: Exception) {
            }

            // try Kotlin `object`
            val objectInstance = loadFromField(clazz, "INSTANCE", returnClass)
            if (objectInstance != null) return objectInstance

            // try Kotlin `Companion` object
            val companionInstance = loadFromField(clazz, "Companion", returnClass)
            if (companionInstance != null) return companionInstance

            return null
        }

        private fun <T> loadFromField(clazz: Class<*>, field: String, returnClass: Class<T>): T? =
            try {
                val fieldInstance = clazz.getDeclaredField(field).get(clazz)
                returnClass.cast(fieldInstance)
            } catch (_: Exception) {
                null
            }
    }
}
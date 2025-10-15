package com.tealium.prism.core.internal.misc

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.internal.modules.ModuleDiscoveryService

class TealiumAutoInitContentProvider: ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application
        if (app == null) {
            Log.d(BuildConfig.TAG, "Auto-init failed.")
            return false
        }

        Log.d(BuildConfig.TAG, "Auto-initializing Tealium")

        ActivityManagerImpl.getInstance(app)

        val modules = ModuleDiscoveryService.discover(app)
        Modules.addDefaultModules(modules)

        Log.d(BuildConfig.TAG, "Auto-init successful")

        return false
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }
}
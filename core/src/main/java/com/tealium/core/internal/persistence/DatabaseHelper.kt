package com.tealium.core.internal.persistence

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import com.tealium.core.TealiumConfig
import com.tealium.core.internal.DataLayerImpl
import com.tealium.core.internal.persistence.Schema.DispatchTable.CREATE_DISPATCH_TABLE
import com.tealium.core.internal.persistence.Schema.QueueTable.CREATE_QUEUE_TABLE
import com.tealium.core.internal.persistence.Schema.DispatcherTable.CREATE_DISPATCHERS_TABLE
import com.tealium.core.internal.persistence.Schema.ModuleStorageTable.CREATE_MODULE_STORAGE_TABLE
import com.tealium.core.internal.persistence.Schema.ModuleTable.CREATE_MODULE_TABLE
import com.tealium.core.internal.persistence.Schema.DispatchTable.CREATE_TRIGGER_ADD_TO_QUEUE
//import com.tealium.core.internal.persistence.Schema.LegacyTables.migrateModuleData
import com.tealium.core.internal.persistence.Schema.QueueTable.CREATE_TRIGGER_REMOVE_PROCESSED_DISPATCHES
import java.io.File

internal class DatabaseHelper(
    private val config: TealiumConfig,
    databaseName: String? = databaseName(config),
) :
    SQLiteOpenHelper(config.application.applicationContext, databaseName, null, DATABASE_VERSION) {

    fun writableDatabaseOrNull(): SQLiteDatabase? {
        return try {
            writableDatabase.takeIf { !it.isReadOnly }
        } catch (ex: SQLiteException) {
            null
        }
    }

    override fun onConfigure(db: SQLiteDatabase?) {
        super.onConfigure(db)

        db?.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db?.let {
            createV3Tables(it)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.let {
            getDatabaseUpgrades(oldVersion).forEach {
                it.upgrade(db)
            }
        }
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.close()
        deleteDatabase()

        throw UnsupportedDowngrade(oldVersion, newVersion)
    }

    fun deleteDatabase() {
        val dbFile = File(databaseName(config))
        SQLiteDatabase.deleteDatabase(dbFile)
    }

    companion object {

        /**
         * The Database version expected by this version of the SDK.
         */
        const val DATABASE_VERSION = 3

        internal val V1_to_V2 = DatabaseUpgrade(
            1
        ) {
            it.execSQL(Schema.LegacyTables.createLegacyTable(Schema.LegacyTables.VISITORS_TABLE_NAME))
        }

        internal val V2_to_V3 = DatabaseUpgrade(
            2
        ) {
            createV3Tables(it)

            migrateModuleData(it, DataLayerImpl.name, Schema.LegacyTables.DATALAYER_TABLE_NAME)
//            // TODO - VisitorSwitching module doesn't exist yet; name might change
            migrateModuleData(it, "VisitorSwitching", Schema.LegacyTables.VISITORS_TABLE_NAME)

            // Dispatch migration has to be handled later, as the Dispatchers needs to be known
            // and registered in order for the triggers to accurately introduce the relevant
            // entries into the queue
        }

        /**
         * Attempts to migrate data from the
         */
        private fun migrateModuleData(
            db: SQLiteDatabase,
            moduleName: String,
            oldTableName: String
        ) {
//            val moduleId = ModuleStorageRepositoryImpl.insertNewModule(
//                db, moduleName
//            )
//            if (moduleId < 0) return
//
//            try {
//                db.execSQL(
//                    migrateModuleData(oldTableName), arrayOf(moduleId)
//                )
//                db.dropTable(oldTableName)
//            } catch (ignored: SQLiteException) {
//                Log.d(BuildConfig.TAG, "Error ${ignored.message}")
//            }
        }

        private fun createV3Tables(db: SQLiteDatabase) {
            db.execSQL(CREATE_DISPATCHERS_TABLE)
            db.execSQL(CREATE_DISPATCH_TABLE)
            db.execSQL(CREATE_QUEUE_TABLE)
            db.execSQL(CREATE_MODULE_TABLE)
            db.execSQL(CREATE_MODULE_STORAGE_TABLE)
            db.execSQL(CREATE_TRIGGER_ADD_TO_QUEUE)
            db.execSQL(CREATE_TRIGGER_REMOVE_PROCESSED_DISPATCHES)
        }

        internal val databaseUpgrades = listOf<DatabaseUpgrade>(
            V1_to_V2, V2_to_V3
        ).sortedBy { it.version }

        fun getDatabaseUpgrades(
            oldVersion: Int,
            upgrades: List<DatabaseUpgrade> = databaseUpgrades
        ): List<DatabaseUpgrade> {
            return upgrades
                .filter { oldVersion < it.version }
        }

        /**
         * Returns a String unique to the Tealium Account/Profile
         *
         */
        fun databaseName(config: TealiumConfig): String {
            return "${config.tealiumDirectory}${File.separatorChar}tealium-${config.accountName}-${config.profileName}.db"
        }
    }

    class UnsupportedDowngrade(
        val oldVersion: Int,
        val newVersion: Int,
        message: String = "Downgrade from $oldVersion to $newVersion is not supported."
    ): Exception(message)
}
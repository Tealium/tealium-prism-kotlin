package com.tealium.core.internal.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tealium.core.TealiumConfig
import org.junit.Assert.fail
import java.util.UUID

object DatabaseTestUtils {

    /**
     * Checks whether this database is an in-memory one rather than persistent
     */
    val SQLiteDatabase.isInMemory: Boolean
        get() = this.path == ":memory:"

    /**
     * Creates an empty Database with the provided version
     */
    fun createBlankDatabase(
        context: Context,
        dbName: String,
        version: Int
    ): SQLiteDatabase {
        return object : SQLiteOpenHelper(
            context, dbName, null, version
        ) {
            override fun onCreate(db: SQLiteDatabase?) {
                // empty
            }

            override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
        }.writableDatabase
    }

    /**
     * Creates a database using the V1 schema in the same way that the previous SDK versions did
     */
    fun createV1Database(context: Context, dbName: String? = null): SQLiteDatabase {
        return object : SQLiteOpenHelper(
            context, dbName, null, 1
        ) {
            override fun onCreate(db: SQLiteDatabase?) {
                db?.execSQL(
                    Schema.LegacyTables.createLegacyTable(Schema.LegacyTables.DATALAYER_TABLE_NAME)
                )
                db?.execSQL(
                    Schema.LegacyTables.createLegacyTable(Schema.LegacyTables.DISPATCHES_TABLE_NAME)
                )
            }

            override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
        }.writableDatabase
    }

    /**
     * Creates a database using the V2 schema in the same way that the previous SDK versions did
     */
    fun createV2Database(context: Context, dbName: String? = null): SQLiteDatabase {
        return object : SQLiteOpenHelper(
            context, dbName, null, 2
        ) {
            override fun onCreate(db: SQLiteDatabase?) {
                db?.execSQL(
                    Schema.LegacyTables.createLegacyTable(Schema.LegacyTables.DATALAYER_TABLE_NAME)
                )
                db?.execSQL(
                    Schema.LegacyTables.createLegacyTable(Schema.LegacyTables.DISPATCHES_TABLE_NAME)
                )
                onUpgrade(db, 1, 2)
            }

            override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
                db?.upgrade(oldVersion, newVersion)
            }
        }.writableDatabase
    }

    /**
     * Creates a database using the V3 schema using the current [DatabaseHelper]
     */
    fun createV3Database(config: TealiumConfig, dbName: String? = null): SQLiteDatabase {
        return DatabaseHelper(config, dbName).writableDatabase
    }

    /**
     * Asserts that all V1 tables exist and none of the V2 or V3 tables exist
     */
    fun assertV1TablesExist(db: SQLiteDatabase) {
        assertTableExists(db, Schema.LegacyTables.DISPATCHES_TABLE_NAME)
        assertTableExists(db, Schema.LegacyTables.DATALAYER_TABLE_NAME)

        assertTableNotExists(db, Schema.LegacyTables.VISITORS_TABLE_NAME)

        assertTableNotExists(db, Schema.DispatchTable.TABLE_NAME)
        assertTableNotExists(db, Schema.QueueTable.TABLE_NAME)
        assertTableNotExists(db, Schema.ModuleTable.TABLE_NAME)
        assertTableNotExists(db, Schema.ModuleStorageTable.TABLE_NAME)
    }

    /**
     * Asserts that all V1 tables exist and none of the V2 or V3 tables exist
     */
    fun assertV2TablesExist(db: SQLiteDatabase) {
        assertTableExists(db, Schema.LegacyTables.DISPATCHES_TABLE_NAME)
        assertTableExists(db, Schema.LegacyTables.DATALAYER_TABLE_NAME)
        assertTableExists(db, Schema.LegacyTables.VISITORS_TABLE_NAME)

        assertTableNotExists(db, Schema.DispatchTable.TABLE_NAME)
        assertTableNotExists(db, Schema.QueueTable.TABLE_NAME)
        assertTableNotExists(db, Schema.ModuleTable.TABLE_NAME)
        assertTableNotExists(db, Schema.ModuleStorageTable.TABLE_NAME)
    }

    /**
     * Asserts that all V3 tables exist.
     */
    fun assertV3TablesExist(db: SQLiteDatabase) {
        assertTableExists(db, Schema.DispatchTable.TABLE_NAME)
        assertTableExists(db, Schema.QueueTable.TABLE_NAME)
        assertTableExists(db, Schema.ModuleTable.TABLE_NAME)
        assertTableExists(db, Schema.ModuleStorageTable.TABLE_NAME)
    }

    /**
     * Asserts that all V3 tables exist and none of the migratable tables in V1 or V2 tables exist
     */
    fun assertV3TablesPostUpgrade(db: SQLiteDatabase) {
        // Should not be migrated by the db updagrade
        assertTableExists(db, Schema.LegacyTables.DATALAYER_TABLE_NAME)
        assertTableExists(db, Schema.LegacyTables.VISITORS_TABLE_NAME)
        assertTableExists(db, Schema.LegacyTables.DISPATCHES_TABLE_NAME)

        assertV3TablesExist(db)
    }

    fun populateV1Database(db: SQLiteDatabase) {
        db.execSQL(POPULATE_LEGACY_DATALAYER)
        db.execSQL(POPULATE_LEGACY_DISPATCHES)
    }

    fun populateV2Database(db: SQLiteDatabase) {
        db.execSQL(POPULATE_LEGACY_DATALAYER)
        db.execSQL(POPULATE_LEGACY_DISPATCHES)
        db.execSQL(POPULATE_LEGACY_VISITORS)
    }

    fun insertLegacyDispatch(
        db: SQLiteDatabase,
        uuid: String = UUID.randomUUID().toString(),
        payload: String = """{"tealium_event_type":"view","tealium_event":"ModuleList","screen_title":"ModuleList","request_uuid":"2a71891f-3e6f-4ac7-a957-22d53da7697d","autotracked":true,"global_data":"value","available_modules":["Consent Manager","Crash Reporter","Hosted Data Layer","Location","Timed Events","Visitor Service","Media Tracking","WebView Consent Sync","In App Purchase"],"tealium_account":"tealiummobile","tealium_profile":"android","tealium_environment":"dev","tealium_datasource":"","tealium_visitor_id":"73ef029f5c7d4648bebeb431b96f0631","tealium_library_name":"android-kotlin","tealium_library_version":"1.5.3","tealium_random":"2541512247698537","tealium_session_id":1686048488589,"app_uuid":"856f9e13-1dfc-4019-a97a-b5ef47351a55","app_rdns":"com.tealium.mobile","app_name":"Tealium Kotlin Example","app_version":"1.0","app_build":"1","app_memory_usage":64,"connection_type":"wifi","device_connected":true,"carrier":"T-Mobile","carrier_iso":"us","carrier_mcc":"310","carrier_mnc":"260","device":"Google sdk_gphone64_arm64","device_model":"sdk_gphone64_arm64","device_manufacturer":"Google","device_architecture":"64bit","device_cputype":"aarch64","device_resolution":"1080x2201","device_logical_resolution":"412x915","device_android_runtime":"2.1.0","origin":"mobile","platform":"android","os_name":"Android","device_os_build":"9526604","device_os_version":"13","device_free_system_storage":50446336,"device_free_external_storage":3984711680,"device_orientation":"Portrait","device_language":"en-US","device_battery_percent":100,"device_ischarging":false,"timestamp":"2023-06-06T10:48:09Z","timestamp_local":"2023-06-06T11:48:09","timestamp_offset":"1","timestamp_unix":1686048489,"timestamp_unix_milliseconds":1686048489502,"timestamp_epoch":1686048489,"remote_commands":["localjsoncommand-0.0","bgcolor-0.0"],"consent_last_updated":1686048488892,"lifecycle_dayofweek_local":3,"lifecycle_dayssincelaunch":"14","lifecycle_dayssincelastwake":"0","lifecycle_hourofday_local":"11","lifecycle_launchcount":2,"lifecycle_sleepcount":0,"lifecycle_wakecount":2,"lifecycle_totalcrashcount":1,"lifecycle_totallaunchcount":2,"lifecycle_totalsleepcount":"0","lifecycle_totalwakecount":"2","lifecycle_totalsecondsawake":"0","lifecycle_dayssinceupdate":"14","lifecycle_firstlaunchdate":"2023-05-23T10:32:55Z","lifecycle_firstlaunchdate_MMDDYYYY":"05\/23\/2023","lifecycle_lastlaunchdate":"2023-06-06T11:48:09Z","lifecycle_lastwakedate":"2023-06-06T11:48:09Z","enabled_modules":["AdIdentifier","AppData","Collect","Connectivity","ConsentManager","Crash","DeviceData","HostedDataLayer","InAppPurchaseManager","Lifecycle","RemoteCommands","TagManagement","VisitorService"],"enabled_modules_versions":["1.1.0","1.5.3","1.1.0","1.5.3","1.5.3","1.1.0","1.5.3","1.1.0","1.0.1","1.1.1","1.3.0","1.2.0","1.2.0"],"was_queued":true}""",
        timestamp: Long = getTimestampMilliseconds()
    ) {
        db.execSQL(
            """
            INSERT INTO dispatches
                VALUES (
                '$uuid',	
                '$payload',	
                -1,	
                $timestamp,	
                10)
            ;
        """.trimIndent()
        )
    }

    fun SQLiteDatabase.upgrade(oldVersion: Int, newVersion: Int) {
        getDatabaseUpgrades(oldVersion, newVersion)
            .forEach {
                it.upgrade(this)
            }
    }

    /**
     * Adapted from V1 DatabaseHelper
     */
    fun getDatabaseUpgrades(
        oldVersion: Int,
        newVersion: Int,
    ): List<DatabaseUpgrade> {
        if (oldVersion > newVersion)
            throw IllegalArgumentException("oldVersion cannot be greater than new")

        @Suppress("ConvertTwoComparisonsToRangeCheck")
        return DatabaseHelper.databaseUpgrades
            .filter {
                it.version > oldVersion && it.version <= newVersion
            }
    }

    /**
     * Helper method to assert that a specific table is present in the Database
     */
    fun assertTableExists(db: SQLiteDatabase, tableName: String) {
        if (getTableCount(db, tableName) <= 0) {
            fail("Table $tableName does not exist when it is expected to.")
        }
    }

    /**
     * Helper method to assert that a specific table is not present in the Database
     */
    fun assertTableNotExists(db: SQLiteDatabase, tableName: String) {
        if (getTableCount(db, tableName) >= 1) {
            fail("Table $tableName exists when it is not expected to.")
        }
    }

    /**
     * Returns the number of tables with the given [tableName] in the database.
     */
    private fun getTableCount(db: SQLiteDatabase, tableName: String): Int {
        db.rawQuery(
            """
                SELECT count(*) as count
                FROM 
                    sqlite_master
                WHERE 
                    type = 'table' AND 
                    name = ?;
            """.trimIndent(),
            arrayOf(tableName)
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(cursor.getColumnIndex("count"))
        }
    }

    private val POPULATE_LEGACY_DATALAYER: String = """
        INSERT INTO datalayer
        VALUES 
            ('string',	'value with spaces',	-2,	null,	0),
            ('boolean_true',	'1',	-2, null,	4),
            ('boolean_false',	'0',	-2, null,	4),
            ('boolean_array',	'[false,true,false]',	-2, null,	9),
            ('int',	'10',	-2, null,	1),
            ('int_array',	'[1,2,3]',	-2, null,	6),
            ('double',	'100.1',	-2, null,	2),
            ('double_array',	'[1.1,2.2,3.3]',	-2, null,	7),
            ('long',	'100',	-2, null,	3),
            ('long_array',	'[100,200,300]',	-2, null,	8),
            ('json_array',	'["test",1,true]',	-2, null,	11),
            ('json_object',	'{"string":"value","int":1,"boolean":true}',	-2, null,	10),
            ('string_array',	'["value1","value2","value3"]',	-2, null,	5),
            ('session_string',	'value',	-2, null,	0),
            ('forever_string',	'value',	-1, null,	0),
            ('after_string',	'value',	1686074812101, null,	0)
        ;
    """.trimIndent()

    private val POPULATE_LEGACY_DISPATCHES: String = """
        INSERT INTO dispatches
        VALUES
        ('2a71891f-3e6f-4ac7-a957-22d53da7697d',	'{"tealium_event_type":"view","tealium_event":"ModuleList","screen_title":"ModuleList","request_uuid":"2a71891f-3e6f-4ac7-a957-22d53da7697d","autotracked":true,"global_data":"value","available_modules":["Consent Manager","Crash Reporter","Hosted Data Layer","Location","Timed Events","Visitor Service","Media Tracking","WebView Consent Sync","In App Purchase"],"tealium_account":"tealiummobile","tealium_profile":"android","tealium_environment":"dev","tealium_datasource":"","tealium_visitor_id":"73ef029f5c7d4648bebeb431b96f0631","tealium_library_name":"android-kotlin","tealium_library_version":"1.5.3","tealium_random":"2541512247698537","tealium_session_id":1686048488589,"app_uuid":"856f9e13-1dfc-4019-a97a-b5ef47351a55","app_rdns":"com.tealium.mobile","app_name":"Tealium Kotlin Example","app_version":"1.0","app_build":"1","app_memory_usage":64,"connection_type":"wifi","device_connected":true,"carrier":"T-Mobile","carrier_iso":"us","carrier_mcc":"310","carrier_mnc":"260","device":"Google sdk_gphone64_arm64","device_model":"sdk_gphone64_arm64","device_manufacturer":"Google","device_architecture":"64bit","device_cputype":"aarch64","device_resolution":"1080x2201","device_logical_resolution":"412x915","device_android_runtime":"2.1.0","origin":"mobile","platform":"android","os_name":"Android","device_os_build":"9526604","device_os_version":"13","device_free_system_storage":50446336,"device_free_external_storage":3984711680,"device_orientation":"Portrait","device_language":"en-US","device_battery_percent":100,"device_ischarging":false,"timestamp":"2023-06-06T10:48:09Z","timestamp_local":"2023-06-06T11:48:09","timestamp_offset":"1","timestamp_unix":1686048489,"timestamp_unix_milliseconds":1686048489502,"timestamp_epoch":1686048489,"remote_commands":["localjsoncommand-0.0","bgcolor-0.0"],"consent_last_updated":1686048488892,"lifecycle_dayofweek_local":3,"lifecycle_dayssincelaunch":"14","lifecycle_dayssincelastwake":"0","lifecycle_hourofday_local":"11","lifecycle_launchcount":2,"lifecycle_sleepcount":0,"lifecycle_wakecount":2,"lifecycle_totalcrashcount":1,"lifecycle_totallaunchcount":2,"lifecycle_totalsleepcount":"0","lifecycle_totalwakecount":"2","lifecycle_totalsecondsawake":"0","lifecycle_dayssinceupdate":"14","lifecycle_firstlaunchdate":"2023-05-23T10:32:55Z","lifecycle_firstlaunchdate_MMDDYYYY":"05\/23\/2023","lifecycle_lastlaunchdate":"2023-06-06T11:48:09Z","lifecycle_lastwakedate":"2023-06-06T11:48:09Z","enabled_modules":["AdIdentifier","AppData","Collect","Connectivity","ConsentManager","Crash","DeviceData","HostedDataLayer","InAppPurchaseManager","Lifecycle","RemoteCommands","TagManagement","VisitorService"],"enabled_modules_versions":["1.1.0","1.5.3","1.1.0","1.5.3","1.5.3","1.1.0","1.5.3","1.1.0","1.0.1","1.1.1","1.3.0","1.2.0","1.2.0"],"was_queued":true}',	-1,	1686048489386,	10),
        ('3533f29e-9284-420d-9c55-b8aab093f7f4',	'{"tealium_event_type":"view","tealium_event":"MainActivity","screen_title":"MainActivity","request_uuid":"3533f29e-9284-420d-9c55-b8aab093f7f4","autotracked":true,"global_data":"value","tealium_account":"tealiummobile","tealium_profile":"android","tealium_environment":"dev","tealium_datasource":"","tealium_visitor_id":"73ef029f5c7d4648bebeb431b96f0631","tealium_library_name":"android-kotlin","tealium_library_version":"1.5.3","tealium_random":"4435668062676114","tealium_session_id":1686048488589,"app_uuid":"856f9e13-1dfc-4019-a97a-b5ef47351a55","app_rdns":"com.tealium.mobile","app_name":"Tealium Kotlin Example","app_version":"1.0","app_build":"1","app_memory_usage":64,"connection_type":"wifi","device_connected":true,"carrier":"T-Mobile","carrier_iso":"us","carrier_mcc":"310","carrier_mnc":"260","device":"Google sdk_gphone64_arm64","device_model":"sdk_gphone64_arm64","device_manufacturer":"Google","device_architecture":"64bit","device_cputype":"aarch64","device_resolution":"1080x2201","device_logical_resolution":"412x915","device_android_runtime":"2.1.0","origin":"mobile","platform":"android","os_name":"Android","device_os_build":"9526604","device_os_version":"13","device_free_system_storage":50446336,"device_free_external_storage":3984711680,"device_orientation":"Portrait","device_language":"en-US","device_battery_percent":100,"device_ischarging":false,"timestamp":"2023-06-06T10:48:09Z","timestamp_local":"2023-06-06T11:48:09","timestamp_offset":"1","timestamp_unix":1686048489,"timestamp_unix_milliseconds":1686048489596,"timestamp_epoch":1686048489,"remote_commands":["localjsoncommand-0.0","bgcolor-0.0"],"consent_last_updated":1686048488892,"lifecycle_dayofweek_local":3,"lifecycle_dayssincelaunch":"14","lifecycle_dayssincelastwake":"0","lifecycle_hourofday_local":"11","lifecycle_launchcount":2,"lifecycle_sleepcount":0,"lifecycle_wakecount":2,"lifecycle_totalcrashcount":1,"lifecycle_totallaunchcount":2,"lifecycle_totalsleepcount":"0","lifecycle_totalwakecount":"2","lifecycle_totalsecondsawake":"0","lifecycle_dayssinceupdate":14,"lifecycle_firstlaunchdate":"2023-05-23T10:32:55Z","lifecycle_firstlaunchdate_MMDDYYYY":"05\/23\/2023","lifecycle_lastlaunchdate":"2023-06-06T11:48:09Z","lifecycle_lastwakedate":"2023-06-06T11:48:09Z","lifecycle_updatelaunchdate":"2023-05-23T09:32:55Z","enabled_modules":["AdIdentifier","AppData","Collect","Connectivity","ConsentManager","Crash","DeviceData","HostedDataLayer","InAppPurchaseManager","Lifecycle","RemoteCommands","TagManagement","VisitorService"],"enabled_modules_versions":["1.1.0","1.5.3","1.1.0","1.5.3","1.5.3","1.1.0","1.5.3","1.1.0","1.0.1","1.1.1","1.3.0","1.2.0","1.2.0"],"was_queued":true}',	-1,	1686048489440,	10),
        ('77b2d8d3-24b1-4c5e-bd61-b699a4aab856',	'{"tealium_event_type":"event","tealium_event":"launch","request_uuid":"77b2d8d3-24b1-4c5e-bd61-b699a4aab856","autotracked":true,"lifecycle_type":"launch","lifecycle_diddetectcrash":"true","lifecycle_totalcrashcount":1,"lifecycle_isfirstwakemonth":"true","lifecycle_isfirstwaketoday":"true","lifecycle_priorsecondsawake":"0","tealium_account":"tealiummobile","tealium_profile":"android","tealium_environment":"dev","tealium_datasource":"","tealium_visitor_id":"73ef029f5c7d4648bebeb431b96f0631","tealium_library_name":"android-kotlin","tealium_library_version":"1.5.3","tealium_random":"0627265084498277","tealium_session_id":1686048488589,"app_uuid":"856f9e13-1dfc-4019-a97a-b5ef47351a55","app_rdns":"com.tealium.mobile","app_name":"Tealium Kotlin Example","app_version":"1.0","app_build":"1","app_memory_usage":64,"connection_type":"wifi","device_connected":true,"carrier":"T-Mobile","carrier_iso":"us","carrier_mcc":"310","carrier_mnc":"260","device":"Google sdk_gphone64_arm64","device_model":"sdk_gphone64_arm64","device_manufacturer":"Google","device_architecture":"64bit","device_cputype":"aarch64","device_resolution":"1080x2201","device_logical_resolution":"412x915","device_android_runtime":"2.1.0","origin":"mobile","platform":"android","os_name":"Android","device_os_build":"9526604","device_os_version":"13","device_free_system_storage":50446336,"device_free_external_storage":3984703488,"device_orientation":"Portrait","device_language":"en-US","device_battery_percent":100,"device_ischarging":false,"timestamp":"2023-06-06T10:48:09Z","timestamp_local":"2023-06-06T11:48:09","timestamp_offset":"1","timestamp_unix":1686048489,"timestamp_unix_milliseconds":1686048489624,"timestamp_epoch":1686048489,"remote_commands":["localjsoncommand-0.0","bgcolor-0.0"],"consent_last_updated":1686048488892,"lifecycle_dayofweek_local":3,"lifecycle_dayssincelaunch":"14","lifecycle_dayssincelastwake":"0","lifecycle_hourofday_local":"11","lifecycle_launchcount":2,"lifecycle_sleepcount":0,"lifecycle_wakecount":2,"lifecycle_totallaunchcount":2,"lifecycle_totalsleepcount":"0","lifecycle_totalwakecount":"2","lifecycle_totalsecondsawake":"0","lifecycle_dayssinceupdate":14,"lifecycle_firstlaunchdate":"2023-05-23T10:32:55Z","lifecycle_firstlaunchdate_MMDDYYYY":"05\/23\/2023","lifecycle_lastlaunchdate":"2023-06-06T11:48:09Z","lifecycle_lastwakedate":"2023-06-06T11:48:09Z","lifecycle_updatelaunchdate":"2023-05-23T09:32:55Z","enabled_modules":["AdIdentifier","AppData","Collect","Connectivity","ConsentManager","Crash","DeviceData","HostedDataLayer","InAppPurchaseManager","Lifecycle","RemoteCommands","TagManagement","VisitorService"],"enabled_modules_versions":["1.1.0","1.5.3","1.1.0","1.5.3","1.5.3","1.1.0","1.5.3","1.1.0","1.0.1","1.1.1","1.3.0","1.2.0","1.2.0"],"was_queued":true}',	-1,	1686048489475,	10)
        ;
    """.trimIndent()

    private val POPULATE_LEGACY_VISITORS: String = """
        INSERT INTO visitors
        VALUES
        ('tealium_visitor_id',	'73ef029f5c7d4648bebeb431b96f0631',	-1, null,		0),
        ('current_identity',	'9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08',	-1,	 null,	0),
        ('9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08',	'73ef029f5c7d4648bebeb431b96f0631',	-1,	 null,	0)
        ;
    """.trimIndent()
}
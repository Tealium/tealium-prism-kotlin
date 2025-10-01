package com.tealium.prism.lifecycle.lifecycleService

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.database.DatabaseProvider
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.prism.lifecycle.internal.LifecycleService
import com.tealium.prism.lifecycle.internal.LifecycleServiceImpl
import com.tealium.prism.lifecycle.internal.LifecycleStorage
import com.tealium.prism.lifecycle.internal.LifecycleStorageImpl
import com.tealium.tests.common.getDefaultConfig
import org.junit.After
import org.junit.Before
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

open class LifecycleServiceBaseTests {
    private val context: Application = ApplicationProvider.getApplicationContext() as Application
    private val config = getDefaultConfig(context)

    private lateinit var dbProvider: DatabaseProvider
    lateinit var lifecycleStorage: LifecycleStorage
    lateinit var lifecycleService: LifecycleService

    val launchTimestamp: Long = 1731061966000
    val launchDateString: String
        get() = formatDate("yyyy-MM-dd'T'HH:mm:ss'Z'", launchTimestamp)
    val launchMmDdYyyyString: String
        get() = formatDate("MM/dd/yyyy", launchTimestamp)
    val millisecondsPerDay: Long = 86400000
    val millisecondsPerHour: Long = 3600000
    val secondsPerDay: Long
        get() = millisecondsPerDay / 1000
    val secondsPerHour: Long
        get() = millisecondsPerHour / 1000

    var lifecycleEventState: DataObject = DataObject.EMPTY_OBJECT
    var customEventState: DataObject = DataObject.EMPTY_OBJECT

    @Before
    open fun setUp() {
        dbProvider = InMemoryDatabaseProvider(config)
        val modulesRepository = SQLModulesRepository(dbProvider)
        val moduleStoreProvider = ModuleStoreProviderImpl(dbProvider, modulesRepository)
        val dataStore = moduleStoreProvider.getSharedDataStore()

        lifecycleStorage = LifecycleStorageImpl(dataStore)

        lifecycleService = LifecycleServiceImpl(context, lifecycleStorage)
    }

    @After
    fun tearDown() {
        dbProvider.database.close()
    }
}

fun formatDate(format: String, timestamp: Long): String {
    val simpleDate = SimpleDateFormat(format, Locale.ROOT)
    simpleDate.timeZone = TimeZone.getTimeZone("UTC")
    val date = Date(timestamp)
    return simpleDate.format(date)
}
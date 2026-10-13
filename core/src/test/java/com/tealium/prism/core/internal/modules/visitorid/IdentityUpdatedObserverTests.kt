package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.repositories.ModulesRepository
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.tests.common.getDefaultConfig
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class IdentityUpdatedObserverTests {

    private lateinit var modulesRepository: SQLModulesRepository
    private lateinit var dataLayer: DataStore
    private lateinit var configuration: StateSubject<VisitorIdConfiguration>
    private lateinit var disposable: Disposable
    private val identified = mutableListOf<String>()

    @Before
    fun setUp() {
        val databaseProvider = InMemoryDatabaseProvider(getDefaultConfig(RuntimeEnvironment.getApplication()))
        modulesRepository = SQLModulesRepository(databaseProvider)
        dataLayer = ModuleStoreProviderImpl(databaseProvider, modulesRepository)
            .getModuleStore(Modules.Types.DATA_LAYER)

        configuration = Observables.stateSubject(VisitorIdConfiguration(visitorIdentityKey = IDENTITY_KEY))
        disposable = subscribe(configuration)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_No_Value_At_Identity_Key() {
        assertEquals(emptyList<String>(), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_When_Value_Already_At_Identity_Key() {
        disposable.dispose()
        storeIdentity("identityValue")

        subscribe(configuration)

        assertEquals(listOf("identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_When_Value_Written_At_Identity_Key() {
        storeIdentity("identityValue")

        assertEquals(listOf("identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_Again_When_Value_Changes_At_Identity_Key() {
        storeIdentity("identityValue")
        storeIdentity("otherIdentity")

        assertEquals(listOf("identityValue", "otherIdentity"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_Twice_When_Same_Value_Written_Twice() {
        storeIdentity("identityValue")
        storeIdentity("identityValue")

        assertEquals(listOf("identityValue", "identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_Value_Removed_From_Identity_Key() {
        storeIdentity("identityValue")
        dataLayer.edit().remove(IDENTITY_KEY).commit()

        assertEquals(listOf("identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_Again_When_Identity_Removed_Then_Rewritten() {
        storeIdentity("identityValue")
        dataLayer.edit().remove(IDENTITY_KEY).commit()
        storeIdentity("identityValue")

        assertEquals(listOf("identityValue", "identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_Put_Then_Remove_In_One_Transaction_And_Identity_Was_Present() {
        storeIdentity("identityValue")
        dataLayer.edit()
            .put(IDENTITY_KEY, "otherIdentity", Expiry.FOREVER)
            .remove(IDENTITY_KEY)
            .commit()

        assertEquals(listOf("identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_Put_Then_Remove_In_One_Transaction_And_No_Identity_Was_Present() {
        dataLayer.edit()
            .put(IDENTITY_KEY, "identityValue", Expiry.FOREVER)
            .remove(IDENTITY_KEY)
            .commit()

        assertEquals(emptyList<String>(), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_Identity_Expires() {
        dataLayer.edit()
            .put(IDENTITY_KEY, "identityValue", Expiry.afterTimeUnit(1, TimeUnit.HOURS))
            .commit()

        purgeExpiredAsIfHoursLater(2)

        assertEquals(listOf("identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_Already_Expired_Value_Written() {
        dataLayer.edit()
            .put(IDENTITY_KEY, "identityValue", Expiry.afterEpochTime(1))
            .commit()

        assertEquals(emptyList<String>(), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_Unrelated_Key_Updated() {
        storeIdentity("newIdentity", key = "unrelatedKey")

        assertEquals(emptyList<String>(), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Watches_New_Key_And_Calls_Identify_With_Its_Value_When_Identity_Key_Changed() {
        storeIdentity("identityValue")
        storeIdentity("newIdentity", key = "newIdentityKey")
        configuration.onNext(VisitorIdConfiguration(visitorIdentityKey = "newIdentityKey"))
        storeIdentity("otherIdentity")

        assertEquals(listOf("identityValue", "newIdentity"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_Identity_Key_Changed_To_Key_With_No_Value() {
        storeIdentity("identityValue")
        configuration.onNext(VisitorIdConfiguration(visitorIdentityKey = "newIdentityKey"))

        assertEquals(listOf("identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Disables_Switching_When_Identity_Key_Null() {
        storeIdentity("identityValue")
        configuration.onNext(VisitorIdConfiguration(visitorIdentityKey = null))
        storeIdentity("otherIdentity")

        assertEquals(listOf("identityValue"), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Emits_Nothing_When_Identity_Key_Null_From_Start() {
        disposable.dispose()
        storeIdentity("identityValue")

        subscribe(Observables.stateSubject(VisitorIdConfiguration(visitorIdentityKey = null)))
        storeIdentity("otherIdentity")

        assertEquals(emptyList<String>(), identified)
    }

    @Test
    fun subscribeIdentityUpdates_Stops_Calling_Identify_When_Disposed() {
        storeIdentity("identityValue")
        disposable.dispose()
        storeIdentity("otherIdentity")

        assertEquals(listOf("identityValue"), identified)
    }

    private fun subscribe(configuration: Observable<VisitorIdConfiguration>): Disposable =
        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer) {
            identified.add(it)
        }

    private fun storeIdentity(value: String, key: String = IDENTITY_KEY) {
        dataLayer.edit().put(key, value, Expiry.FOREVER).commit()
    }

    /**
     * Runs the repository expiry sweep with a timestamp [hours] in the future, so that rows with a
     * time based expiry inside that window are deleted and published through `onDataExpired`.
     */
    private fun purgeExpiredAsIfHoursLater(hours: Long) {
        modulesRepository.deleteExpired(
            ModulesRepository.ExpirationType.UntilRestart,
            TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.HOURS.toSeconds(hours)
        )
    }

    private companion object {
        const val IDENTITY_KEY = "email"
    }
}

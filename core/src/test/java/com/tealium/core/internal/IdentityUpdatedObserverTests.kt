package com.tealium.core.internal

import com.tealium.core.api.DataStore
import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.Subject
import com.tealium.core.internal.settings.CoreSettings
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class IdentityUpdatedObserverTests {

    @RelaxedMockK
    private lateinit var visitorIdProvider: VisitorIdProvider

    @MockK
    private lateinit var dataLayer: DataStore

    private val defaultIdentityKey = "identity"
    private lateinit var onDataLayerUpdated: Subject<TealiumBundle>
    private lateinit var coreSettings: Subject<CoreSettings>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        onDataLayerUpdated = spyk(Observables.publishSubject())
        every { dataLayer.onDataUpdated } returns onDataLayerUpdated
        every { dataLayer.getString(defaultIdentityKey) } returns null

        coreSettings = Observables.stateSubject(CoreSettings(visitorIdentityKey = defaultIdentityKey))
    }

    @Test
    fun subscribeIdentityUpdates_Subscribes_To_DataLayer_Updates_When_Key_IsNot_Null() {
        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)

        verify {
            onDataLayerUpdated.subscribe(any())
        }
    }

    @Test
    fun subscribeIdentityUpdates_DoesNot_Subscribe_To_DataLayer_Updates_When_Key_IsNull() {
        coreSettings.onNext(CoreSettings(visitorIdentityKey = null))
        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)

        verify(inverse = true) {
            onDataLayerUpdated.subscribe(any())
        }
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_With_Initial_Value_From_DataLayer() {
        every { dataLayer.getString(defaultIdentityKey) } returns "identity"

        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)

        verify {
            visitorIdProvider.identify("identity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_DoesNot_Call_Identify_With_Initial_Value_From_DataLayer_When_Key_IsNull() {
        coreSettings.onNext(CoreSettings(visitorIdentityKey = null))

        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)

        verify(inverse = true) {
            visitorIdProvider.identify(any())
        }
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_When_DataLayer_Updated_With_Correct_Key() {
        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)

        onDataLayerUpdated.onNext(TealiumBundle.create {
            put(defaultIdentityKey, "identity")
        })

        verify {
            visitorIdProvider.identify("identity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_DoesNot_Call_Identify_When_DataLayer_Updated_With_Incorrect_Key() {
        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)
        onDataLayerUpdated.onNext(TealiumBundle.create {
            put("incorrectKey", "identity")
        })

        verify(inverse = true) {
            visitorIdProvider.identify("identity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_OnlyEmits_LatestUpdates_When_IdentityKey_Updated() {
        every { dataLayer.getString(defaultIdentityKey) } returns "identity"
        every { dataLayer.getString("newKey") } returns "newIdentity"

        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)

        coreSettings.onNext(CoreSettings(visitorIdentityKey = "newKey"))

        onDataLayerUpdated.onNext(TealiumBundle.create {
            put("newKey", "newIdentity")
        })
        onDataLayerUpdated.onNext(TealiumBundle.create {
            put(defaultIdentityKey, "identity")
        })

        verify {
            visitorIdProvider.identify("newIdentity")
        }
        verify(exactly = 1) {
            visitorIdProvider.identify("identity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_Fetches_Identity_From_New_IdentityKey_When_IdentityKey_Updated() {
        every { dataLayer.getString("newKey") } returns "newIdentity"

        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)
        coreSettings.onNext(CoreSettings(visitorIdentityKey = "newKey"))

        verify {
            visitorIdProvider.identify("newIdentity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_StopsEmitting_Identity_Updates_When_IdentityKey_Null() {
        IdentityUpdatedObserver.subscribeIdentityUpdates(coreSettings, dataLayer, visitorIdProvider)
        coreSettings.onNext(CoreSettings(visitorIdentityKey = null))

        onDataLayerUpdated.onNext(TealiumBundle.create {
            put(defaultIdentityKey, "identity")
        })

        verify(inverse = true) {
            visitorIdProvider.identify("identity")
        }
    }
}
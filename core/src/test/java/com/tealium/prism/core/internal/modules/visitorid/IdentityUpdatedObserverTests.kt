package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.persistence.VisitorIdStorage
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
    private lateinit var visitorIdStorage: VisitorIdStorage

    @MockK
    private lateinit var dataLayer: DataStore

    private val defaultIdentityKey = "identity"
    private lateinit var onDataLayerUpdated: Subject<DataObject>
    private lateinit var configuration: Subject<VisitorIdConfiguration>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        onDataLayerUpdated = spyk(Observables.publishSubject())
        every { dataLayer.onDataUpdated } returns onDataLayerUpdated
        every { dataLayer.getString(defaultIdentityKey) } returns null

        configuration =
            Observables.stateSubject(VisitorIdConfiguration(visitorIdentityKey = defaultIdentityKey))
    }

    @Test
    fun subscribeIdentityUpdates_Subscribes_To_DataLayer_Updates_When_Key_IsNot_Null() {
        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)

        verify {
            onDataLayerUpdated.subscribe(any<Observer<DataObject>>())
        }
    }

    @Test
    fun subscribeIdentityUpdates_DoesNot_Subscribe_To_DataLayer_Updates_When_Key_IsNull() {
        configuration.onNext(VisitorIdConfiguration(visitorIdentityKey = null))
        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)

        verify(inverse = true) {
            onDataLayerUpdated.subscribe(any<Observer<DataObject>>())
        }
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_With_Initial_Value_From_DataLayer() {
        every { dataLayer.getString(defaultIdentityKey) } returns "identity"

        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)

        verify {
            visitorIdStorage.identify("identity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_DoesNot_Call_Identify_With_Initial_Value_From_DataLayer_When_Key_IsNull() {
        every { dataLayer.getString(defaultIdentityKey) } returns "identity"

        configuration.onNext(VisitorIdConfiguration(visitorIdentityKey = null))

        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)

        verify(inverse = true) {
            visitorIdStorage.identify(any())
        }
    }

    @Test
    fun subscribeIdentityUpdates_Calls_Identify_When_DataLayer_Updated_With_Correct_Key() {
        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)

        onDataLayerUpdated.onNext(DataObject.create {
            put(defaultIdentityKey, "identity")
        })

        verify {
            visitorIdStorage.identify("identity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_DoesNot_Call_Identify_When_DataLayer_Updated_With_Incorrect_Key() {
        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)
        onDataLayerUpdated.onNext(DataObject.create {
            put("incorrectKey", "identity")
        })

        verify(inverse = true) {
            visitorIdStorage.identify("identity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_OnlyEmits_LatestUpdates_When_IdentityKey_Updated() {
        every { dataLayer.getString(defaultIdentityKey) } returns "identity"
        every { dataLayer.getString("newKey") } returns "newIdentity"

        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)

        configuration.onNext(VisitorIdConfiguration(visitorIdentityKey = "newKey"))

        onDataLayerUpdated.onNext(DataObject.create {
            put("newKey", "newIdentity")
        })
        onDataLayerUpdated.onNext(DataObject.create {
            put(defaultIdentityKey, "identity")
        })

        verify {
            visitorIdStorage.identify("newIdentity")
        }
        verify(exactly = 1) {
            visitorIdStorage.identify("identity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_Fetches_Identity_From_New_IdentityKey_When_IdentityKey_Updated() {
        every { dataLayer.getString("newKey") } returns "newIdentity"

        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)
        configuration.onNext(VisitorIdConfiguration(visitorIdentityKey = "newKey"))

        verify {
            visitorIdStorage.identify("newIdentity")
        }
    }

    @Test
    fun subscribeIdentityUpdates_StopsEmitting_Identity_Updates_When_IdentityKey_Null() {
        IdentityUpdatedObserver.subscribeIdentityUpdates(configuration, dataLayer, visitorIdStorage)
        configuration.onNext(VisitorIdConfiguration(visitorIdentityKey = null))

        onDataLayerUpdated.onNext(DataObject.create {
            put(defaultIdentityKey, "identity")
        })

        verify(inverse = true) {
            visitorIdStorage.identify("identity")
        }
    }
}
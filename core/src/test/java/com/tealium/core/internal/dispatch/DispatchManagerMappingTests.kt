package com.tealium.core.internal.dispatch

import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.TransformationScope
import com.tealium.tests.common.TestTransformer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DispatchManagerMappingTests : DispatchManagerTestsBase() {

    override fun onAfterSetup() {
        // default engine does no remapping
        mappingsEngine = mockk()
        every { mappingsEngine.map(any(), any()) } answers {
            arg(1)
        }
        dispatchManager = createDispatchManager(mappingsEngine = mappingsEngine)
        dispatchManager.startDispatchLoop()
    }

    @Test
    fun dispatchManager_Maps_Dispatch_Before_Sending_To_Dispatcher() {
        dispatchManager.track(dispatch1)

        verify {
            mappingsEngine.map(dispatcher1Name, dispatch1)
            mappingsEngine.map(dispatcher2Name, dispatch1)
            dispatcher1.dispatch(match { it.contains(dispatch1) }, any())
            dispatcher2.dispatch(match { it.contains(dispatch1) }, any())
        }
    }

    @Test
    fun dispatchManager_Does_Not_Map_Dispatch_If_Dropped_By_Transformer() {
        registerTransformation(scope = setOf(TransformationScope.AllDispatchers)) { _, _, _ -> null }

        dispatchManager.track(dispatch1)

        verify(inverse = true) {
            mappingsEngine.map(dispatcher1Name, dispatch1)
            mappingsEngine.map(dispatcher2Name, dispatch1)
        }
    }
}
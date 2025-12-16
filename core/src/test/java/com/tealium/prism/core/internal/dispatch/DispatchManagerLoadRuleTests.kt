package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.tests.common.TestTransformer
import io.mockk.every
import io.mockk.verify
import org.junit.Test

class DispatchManagerLoadRuleTests : DispatchManagerTestsBase() {

    override fun onAfterSetup() {
        dispatchManager.startDispatchLoop()
    }

    @Test
    fun dispatchManager_Does_Dispatch_Events_That_Satisfy_Load_Rules() {
        dispatchManager.track(dispatch1)

        verify {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_Does_Not_Dispatch_Events_That_Fail_Load_Rules() {
        every { loadRuleEngine.evaluateLoadRules(dispatcher1, listOf(dispatch1)) } answers {
            DispatchSplit(emptyList(), arg(1))
        }
        dispatchManager.track(dispatch1)

        verify(inverse = true) {
            dispatcher1.dispatch(listOf(dispatch1), any())
        }
    }

    @Test
    fun dispatchManager_Evaluates_Load_Rules_After_Transformations() {
        val transformer = TestTransformer.mock("transformer") { _, dispatch, _ ->
            dispatch.apply {
                addAll(DataObject.create { put("transformed", "value") })
            }
        }
        val transformation = TransformationSettings(
            "tr-1",
            "transformer",
            setOf(TransformationScope.Dispatcher(dispatcher1Name))
        )
        transformers.onNext(listOf(transformer))
        transformations.onNext(setOf(transformation))

        dispatchManager.track(dispatch1)

        verify {
            dispatcher1.dispatch(match {
                it[0].payload().getString("transformed") == "value"
            }, any())
        }
    }

    @Test
    fun dispatchManager_Marks_Events_That_Fail_Load_Rules_As_Processed() {
        every { loadRuleEngine.evaluateLoadRules(dispatcher1, listOf(dispatch1)) } answers {
            DispatchSplit(emptyList(), arg(1))
        }

        dispatchManager.track(dispatch1)

        verify {
            queueManager.deleteDispatches(listOf(dispatch1), dispatcher1Name)
        }
    }
}
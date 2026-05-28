package com.tealium.tests.common

import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import io.mockk.every
import io.mockk.mockk

class TestTransformer(
    override val id: String,
    override val version: String = "1.0",
    private val transformHandler: (
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope
    ) -> Dispatch? = { _, dispatch, _ -> dispatch }
) : Transformer {

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val result = transformHandler.invoke(transformation, dispatch, scope)
        completion(result)
    }

    companion object {
        fun mock(
            name: String,
            version: String = "1.0",
            transformHandler: ((
                transformation: TransformationSettings,
                dispatch: Dispatch,
                scope: DispatchScope
            ) -> Dispatch?) = { _, dispatch, _ -> dispatch }
        ): Transformer {
            val mockTransformer = mockk<Transformer>()
            every { mockTransformer.id } returns name
            every { mockTransformer.version } returns version

            every {
                mockTransformer.applyTransformation(any(), any(), any(), any())
            } answers {
                val dispatch =
                    transformHandler.invoke(firstArg(), secondArg(), thirdArg())

                val completion = arg<(Dispatch?) -> Unit>(3)
                completion(dispatch)
            }

            return mockTransformer
        }
    }
}
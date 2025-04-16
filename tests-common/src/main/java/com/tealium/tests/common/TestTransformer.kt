package com.tealium.tests.common

import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.api.transform.Transformer
import io.mockk.spyk

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
            ) -> Dispatch?)? = null
        ): TestTransformer {
            return if (transformHandler == null)
                spyk(TestTransformer(name, version))
            else spyk(TestTransformer(name, version, transformHandler))
        }
    }
}
package com.tealium.core.api

interface Transformer {
    // TODO - consider what might be required by the Transformations module
    fun transform(dispatch: Dispatch)
}
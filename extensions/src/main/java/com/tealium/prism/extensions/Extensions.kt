package com.tealium.prism.extensions

import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.extensions.internal.ExtensionFactory
import com.tealium.prism.extensions.internal.SET_DATA_VALUES
import com.tealium.prism.extensions.internal.SetDataValuesTransformer

object SetDataValuesFactory : ModuleFactory by
    ExtensionFactory(SET_DATA_VALUES, ::SetDataValuesTransformer)

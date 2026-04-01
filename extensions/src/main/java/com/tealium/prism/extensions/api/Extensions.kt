package com.tealium.prism.extensions.api

import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.extensions.internal.ExtensionFactory
import com.tealium.prism.extensions.internal.LOWERCASE
import com.tealium.prism.extensions.internal.PERSIST_DATA_VALUES
import com.tealium.prism.extensions.internal.SET_DATA_VALUES
import com.tealium.prism.extensions.internal.lowercase.LowerCaseTransformer
import com.tealium.prism.extensions.internal.persistdatavalue.PersistDataValuesTransformer
import com.tealium.prism.extensions.internal.setdatavalues.SetDataValuesTransformer

object SetDataValuesFactory : ModuleFactory by
ExtensionFactory(SET_DATA_VALUES, ::SetDataValuesTransformer)

object LowerCaseFactory : ModuleFactory by
ExtensionFactory(LOWERCASE, ::LowerCaseTransformer)

object PersistDataValuesFactory : ModuleFactory by
ExtensionFactory(PERSIST_DATA_VALUES, ::PersistDataValuesTransformer)

package com.tealium.prism.extensions.api

import com.tealium.prism.core.api.modules.ModuleFactory
import com.tealium.prism.extensions.internal.ExtensionFactory
import com.tealium.prism.extensions.internal.LOWERCASE
import com.tealium.prism.extensions.internal.PERSIST_DATA_VALUE
import com.tealium.prism.extensions.internal.SET_DATA_VALUES
import com.tealium.prism.extensions.internal.lowercase.LowerCaseTransformer
import com.tealium.prism.extensions.internal.persistdatavalue.PersistDataValueTransformer
import com.tealium.prism.extensions.internal.setdatavalues.SetDataValuesTransformer

object SetDataValuesFactory : ModuleFactory by
ExtensionFactory(SET_DATA_VALUES, ::SetDataValuesTransformer)

object LowerCaseFactory : ModuleFactory by
ExtensionFactory(LOWERCASE, ::LowerCaseTransformer)

object PersistDataValueFactory : ModuleFactory by
ExtensionFactory(PERSIST_DATA_VALUE, ::PersistDataValueTransformer)

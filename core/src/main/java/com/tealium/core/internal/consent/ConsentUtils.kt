package com.tealium.core.internal.consent

import com.tealium.core.api.consent.ConsentDecision
import com.tealium.core.api.data.DataItem
import com.tealium.core.api.data.DataItemUtils.asDataList
import com.tealium.core.api.data.DataList
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.internal.settings.consent.ConsentConfiguration
import com.tealium.core.internal.settings.consent.ConsentPurpose


fun Dispatch.applyDecision(decision: ConsentDecision): Dispatch? {
    val processedPurposes = this.payload()
        .getDataList(Dispatch.Keys.ALL_CONSENTED_PURPOSES) ?: DataList.EMPTY_LIST

    val unprocessedPurposes = decision.purposes.filter { purpose ->
        processedPurposes.find {
            it.value == purpose
        } == null
    }
    if (unprocessedPurposes.isEmpty()) return null

    this.addAll(DataObject.create {
        put(Dispatch.Keys.UNPROCESSED_PURPOSES, unprocessedPurposes.asDataList())
        put(Dispatch.Keys.PROCESSED_PURPOSES, processedPurposes)
        put(Dispatch.Keys.ALL_CONSENTED_PURPOSES, decision.purposes.asDataList())
        put(Dispatch.Keys.CONSENT_TYPE, decision.decisionType)
    })

    return this
}

fun Dispatch.matchesConfiguration(configuration: ConsentConfiguration, dispatcherId: String): Boolean {
    val consentedPurposes = payload().getDataList(Dispatch.Keys.ALL_CONSENTED_PURPOSES)
        ?.mapNotNull(DataItem::getString)
        ?: return false

    val requiredPurposes = configuration.purposes.values
        .filter { it.dispatcherIds.contains(dispatcherId) }
        .map(ConsentPurpose::purposeId)

    return requiredPurposes.all { consentedPurposes.contains(it) }
}
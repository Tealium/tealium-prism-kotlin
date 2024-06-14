package com.tealium.core.api.consent

import com.tealium.core.api.ConsentStatus

//TODO - this will be the publicly accessible interface of the consent module.
interface ConsentManager {
    var consentStatus: ConsentStatus

}

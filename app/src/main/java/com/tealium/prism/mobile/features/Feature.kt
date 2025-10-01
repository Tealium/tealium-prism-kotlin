package com.tealium.prism.mobile.features

import androidx.fragment.app.Fragment
import com.tealium.prism.mobile.fragments.ConsentFragment
import com.tealium.prism.mobile.fragments.VisitorFragment

enum class Feature(
    val featureName: String,
    val fragment: Class<out Fragment>
) {
    Consent("Consent", ConsentFragment::class.java),
    Visitor("Visitor", VisitorFragment::class.java),
    // TODO - add other features
}
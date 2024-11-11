package com.tealium.mobile.features

import androidx.fragment.app.Fragment
import com.tealium.mobile.fragments.VisitorFragment

enum class Feature(
    val featureName: String,
    val fragment: Class<out Fragment>
) {
    Visitor("Visitor", VisitorFragment::class.java)
    // TODO - add other features
}
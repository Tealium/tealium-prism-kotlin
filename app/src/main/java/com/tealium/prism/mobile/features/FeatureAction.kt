package com.tealium.prism.mobile.features

data class FeatureAction(
    val title: String,
    val action: () -> Unit
)
package com.tealium.mobile.features

data class FeatureAction(
    val title: String,
    val action: () -> Unit
)
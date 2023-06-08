package com.tealium.tests.common

import com.tealium.core.api.Module

class TestModule(
    override val name: String,
    override val version: String = "0.0"
): Module
package com.tealium.prism.mobile

import android.app.Application
import android.os.StrictMode

class App: Application() {
    override fun onCreate() {
        StrictMode.enableDefaults()
        super.onCreate()

        TealiumHelper.init(this)
    }
}
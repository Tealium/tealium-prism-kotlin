package com.tealium.prism.mobile

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class Activity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_two)

        findViewById<Button>(R.id.btn_track_event).setOnClickListener {
            TealiumHelper.track("ButtonClick")
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }
}
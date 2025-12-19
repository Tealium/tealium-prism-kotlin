package com.tealium.prism.mobile

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.tealium.prism.core.api.data.DataObject

class Activity2 : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_two)

        findViewById<Button>(R.id.btn_track_event).setOnClickListener {
            TealiumHelper.track("ButtonClick", data = DataObject.create {
                put("event_category", "EXAMPLE")
                put("event_action", "tap")
                put("event_label", "Track Event from Activity2")
            })
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }
}
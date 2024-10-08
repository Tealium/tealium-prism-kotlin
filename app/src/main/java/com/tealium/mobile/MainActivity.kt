package com.tealium.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_track_event).setOnClickListener {
            TealiumHelper.track("ButtonClick")
        }

        findViewById<Button>(R.id.btn_second_activity).setOnClickListener {
            startActivity(Intent(this@MainActivity, Activity2::class.java))
        }
    }
}
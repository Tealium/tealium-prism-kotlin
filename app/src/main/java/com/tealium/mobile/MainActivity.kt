package com.tealium.mobile

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.tealium.mobile.databinding.ActivityMainBinding
import com.tealium.mobile.features.Feature
import com.tealium.mobile.fragments.FeatureListFragment

class MainActivity : AppCompatActivity(), FeatureListFragment.FeatureSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var trackEventButton: Button
    private lateinit var secondActivityButton: Button
    private lateinit var fragmentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        trackEventButton = binding.btnTrackEvent
        secondActivityButton = binding.btnSecondActivity
        fragmentContainer = binding.fragmentContainer

        loadFragment(FeatureListFragment::class.java)

        trackEventButton.setOnClickListener {
            TealiumHelper.track("ButtonClick")
        }
        secondActivityButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, Activity2::class.java))
        }
    }

    private fun loadFragment(fragment: Class<out Fragment>) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment, null)
            .addToBackStack(null)
            .commit()
    }

    override fun onFeatureSelected(feature: Feature) {
        loadFragment(feature.fragment)
    }
}
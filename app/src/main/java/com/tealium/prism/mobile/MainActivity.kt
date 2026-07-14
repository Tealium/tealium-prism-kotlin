package com.tealium.prism.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.mobile.databinding.ActivityMainBinding
import com.tealium.prism.mobile.features.Feature
import com.tealium.prism.mobile.fragments.FeatureListFragment
import com.tealium.prism.mobile.viewmodels.MainActivityViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), FeatureListFragment.FeatureSelectedListener {

    private val viewModel: MainActivityViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var trackEventButton: Button
    private lateinit var secondActivityButton: Button
    private lateinit var flushButton: Button
    private lateinit var fragmentContainer: LinearLayout
    private lateinit var switchEnabled: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        trackEventButton = binding.btnTrackEvent
        secondActivityButton = binding.btnSecondActivity
        fragmentContainer = binding.fragmentContainer
        flushButton = binding.btnFlushEvents

        switchEnabled = binding.switchTealiumEnabled
        switchEnabled.isChecked = viewModel.isEnabled
        switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                viewModel.initialize(this.application)
            } else {
                viewModel.shutdown()
            }
        }

        loadFragment(FeatureListFragment::class.java, addToBackStack = false)

        trackEventButton.setOnClickListener {
            viewModel.track("ButtonClick", DataObject.create {
                put("event_category", "EXAMPLE")
                put("event_action", "tap")
                put("event_label", "Track Event")
            })
        }
        secondActivityButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, Activity2::class.java))
        }
        flushButton.setOnClickListener {
            viewModel.flush()
        }

        subscribeSnackbarNotifications()
    }

    private fun loadFragment(fragment: Class<out Fragment>, addToBackStack: Boolean = true) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_container, fragment, null)
            .apply { if (addToBackStack) addToBackStack(null) }
            .commit()
    }

    override fun onFeatureSelected(feature: Feature) {
        loadFragment(feature.fragment)
    }

    private fun subscribeSnackbarNotifications() {
        lifecycleScope.launch {
            viewModel.notifications.collect { notification ->
                Snackbar.make(
                    this@MainActivity,
                    binding.mainLayout,
                    notification,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
}
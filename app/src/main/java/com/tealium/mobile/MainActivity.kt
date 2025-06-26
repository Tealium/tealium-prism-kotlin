package com.tealium.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.tealium.mobile.databinding.ActivityMainBinding
import com.tealium.mobile.features.Feature
import com.tealium.mobile.fragments.FeatureListFragment
import com.tealium.mobile.viewmodels.MainActivityViewModel
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        loadFragment(FeatureListFragment::class.java)

        trackEventButton.setOnClickListener {
            viewModel.track("ButtonClick")
        }
        secondActivityButton.setOnClickListener {
            startActivity(Intent(this@MainActivity, Activity2::class.java))
        }
        flushButton.setOnClickListener {
            viewModel.flush()
        }

        subscribeSnackbarNotifications()
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
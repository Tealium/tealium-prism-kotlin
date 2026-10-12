package com.tealium.prism.mobile.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.tealium.prism.mobile.R
import com.tealium.prism.mobile.features.Feature
import com.tealium.prism.mobile.features.FeatureAction
import com.tealium.prism.mobile.viewmodels.VisitorFragmentViewModel
import kotlinx.coroutines.launch

class VisitorFragment : BaseFeatureFragment(Feature.Visitor) {

    private val viewModel: VisitorFragmentViewModel by viewModels()

    override val actions: List<FeatureAction> =
        listOf(
            FeatureAction("Get Visitor Id") {
                viewModel.currentVisitorId()
            },
            FeatureAction("Regenerate Visitor Id") {
                viewModel.regenerateVisitorId()
            },
            FeatureAction("Erase Local Visitor History") {
                viewModel.eraseLocalHistory()
            }
        )

    override fun onAttach(context: Context) {
        super.onAttach(context)
        lifecycleScope.launch {
            viewModel.visitorIdFlow.collect { visitorId ->
                Snackbar.make(
                    requireActivity().findViewById(R.id.main_layout),
                    "Visitor Id has been updated: \n$visitorId",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.textViewFeatureStatus.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.latestVisitorId.collect { latest ->
                    binding.textViewFeatureStatus.text = latest
                }
            }
        }
    }
}

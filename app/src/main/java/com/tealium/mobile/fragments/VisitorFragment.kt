package com.tealium.mobile.fragments

import android.content.Context
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.tealium.mobile.R
import com.tealium.mobile.features.Feature
import com.tealium.mobile.features.FeatureAction
import com.tealium.mobile.viewmodels.VisitorFragmentViewModel
import kotlinx.coroutines.launch

class VisitorFragment : BaseFeatureFragment(Feature.Visitor) {

    private val viewModel: VisitorFragmentViewModel by viewModels()

    override val actions: List<FeatureAction> =
        listOf(
            FeatureAction("Reset Visitor Id") {
                viewModel.resetVisitorId()
            },
            FeatureAction("Clear Stored Visitor Ids") {
                viewModel.clearStoredVisitorIds()
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
}
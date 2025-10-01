package com.tealium.prism.mobile.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.snackbar.Snackbar
import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.mobile.R
import com.tealium.prism.mobile.databinding.FragmentConsentSettingsBinding
import com.tealium.prism.mobile.viewmodels.ConsentFragmentViewModel

class ConsentFragment : Fragment() {

    private val viewModel: ConsentFragmentViewModel by viewModels()
    private lateinit var binding: FragmentConsentSettingsBinding

    // UI elements
    private lateinit var radioGroupConsentType: RadioGroup
    private lateinit var radioImplicit: RadioButton
    private lateinit var radioExplicit: RadioButton
    private lateinit var purposeCheckboxesContainer: LinearLayout
    private lateinit var buttonSaveConsent: Button
    private lateinit var buttonBack: Button

    // Map to store purpose checkboxes
    private val purposeCheckboxes = mutableMapOf<String, CheckBox>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentConsentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI elements
        radioGroupConsentType = binding.radioGroupConsentType
        radioImplicit = binding.radioImplicit
        radioExplicit = binding.radioExplicit
        purposeCheckboxesContainer = binding.purposeCheckboxesContainer
        buttonSaveConsent = binding.buttonSaveConsent
        buttonBack = binding.buttonBack

        // Set up radio buttons
        radioImplicit.setOnClickListener {
            viewModel.setConsentDecisionType(ConsentDecision.DecisionType.Implicit)
        }

        radioExplicit.setOnClickListener {
            viewModel.setConsentDecisionType(ConsentDecision.DecisionType.Explicit)
        }

        // Set up back button
        buttonBack.setOnClickListener {
            handleBackPress()
        }

        // Set up save button
        buttonSaveConsent.setOnClickListener {
            viewModel.saveConsentSettings()
            showConfirmation("Consent settings updated")
        }

        // Handle system back button press
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        // Create checkboxes from available purposes
        createPurposeCheckboxes(viewModel.availablePurposes)

        // Observe consent decision type
        viewModel.consentDecisionType.observe(viewLifecycleOwner, Observer { decisionType ->
            when (decisionType) {
                ConsentDecision.DecisionType.Implicit -> radioImplicit.isChecked = true
                ConsentDecision.DecisionType.Explicit -> radioExplicit.isChecked = true
            }
        })

        // Observe selected purposes
        viewModel.selectedPurposes.observe(viewLifecycleOwner, Observer { selectedPurposes ->
            updatePurposeCheckboxes(selectedPurposes)
        })
    }

    private fun createPurposeCheckboxes(purposes: Set<String>) {
        // Clear existing checkboxes
        purposeCheckboxesContainer.removeAllViews()
        purposeCheckboxes.clear()

        // Create a checkbox for each purpose
        for (purpose in purposes) {
            val checkbox = CheckBox(requireContext()).apply {
                text = purpose.replaceFirstChar(Char::titlecase)
                textSize = 16f
                setPadding(8, 8, 8, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

                // Set up checkbox listener
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.togglePurpose(purpose, isChecked)
                }
            }

            // Add checkbox to container and map
            purposeCheckboxesContainer.addView(checkbox)
            purposeCheckboxes[purpose] = checkbox
        }
    }

    private fun updatePurposeCheckboxes(selectedPurposes: Set<String>) {
        // Update checkbox states based on selected purposes
        for ((purpose, checkbox) in purposeCheckboxes) {
            checkbox.isChecked = selectedPurposes.contains(purpose)
        }
    }

    private fun showConfirmation(message: String) {
        Snackbar.make(
            requireActivity().findViewById(R.id.main_layout),
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    /**
     * Handle back button press. If there are unsaved changes, show a confirmation dialog.
     * Otherwise, navigate back.
     */
    private fun handleBackPress() {
        val hasChanges = viewModel.hasUnsavedChanges.value

        if (hasChanges == null || !hasChanges) {
            navigateBack()
        } else {
            showUnsavedChangesDialog()
        }
    }

    /**
     * Show a dialog warning about unsaved changes.
     */
    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to discard them?")
            .setPositiveButton("Discard") { _, _ ->
                navigateBack()
            }
            .setNegativeButton("Continue Editing", null)
            .show()
    }

    /**
     * Navigate back to the previous screen.
     */
    private fun navigateBack() {
        parentFragmentManager.popBackStack()
    }
}

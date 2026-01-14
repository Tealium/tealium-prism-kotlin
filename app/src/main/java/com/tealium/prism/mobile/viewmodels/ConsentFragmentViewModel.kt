package com.tealium.prism.mobile.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tealium.prism.core.api.consent.ConsentDecision
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.mobile.TealiumHelper

class ConsentFragmentViewModel : ViewModel() {

    private val cmp = TealiumHelper.cmp
    private var cmpDecision: ConsentDecision? = null
    private val disposables: CompositeDisposable = Disposables.composite()

    val availablePurposes: Set<String>
        get() = cmp.allPurposes ?: emptySet()

    private val _consentDecisionType = MutableLiveData<ConsentDecision.DecisionType>()
    val consentDecisionType: LiveData<ConsentDecision.DecisionType> = _consentDecisionType

    private val _selectedPurposes = MutableLiveData<Set<String>>()
    val selectedPurposes: LiveData<Set<String>> = _selectedPurposes

    private val _hasUnsavedChanges = MutableLiveData(false)
    val hasUnsavedChanges: LiveData<Boolean> = _hasUnsavedChanges

    init {
        // Load current consent settings
        cmp.consentDecision
            .subscribe { currentDecision ->
                cmpDecision = currentDecision

                if (_consentDecisionType.value == null || _selectedPurposes.value == null) {
                    // Set initial values
                    val decision = currentDecision ?: cmp.defaultDecision
                    _consentDecisionType.value = decision.decisionType
                    _selectedPurposes.value = decision.purposes
                }
            }.addTo(disposables)
    }

    override fun onCleared() {
        disposables.dispose()
    }

    /**
     * Set the consent decision type.
     * @param decisionType The consent decision type to set
     */
    fun setConsentDecisionType(decisionType: ConsentDecision.DecisionType) {
        _consentDecisionType.value = decisionType
        checkForChanges()
    }

    /**
     * Toggle the selection of a purpose.
     * @param purpose The purpose to toggle
     * @param isSelected Whether the purpose is selected
     */
    fun togglePurpose(purpose: String, isSelected: Boolean) {
        val currentPurposes = _selectedPurposes.value?.toMutableSet() ?: mutableSetOf()
        if (isSelected) {
            currentPurposes.add(purpose)
        } else {
            currentPurposes.remove(purpose)
        }
        _selectedPurposes.value = currentPurposes
        checkForChanges()
    }

    /**
     * Check if there are unsaved changes by comparing current values with original values.
     * Updates the _hasUnsavedChanges LiveData.
     */
    private fun checkForChanges() {
        val decisionTypeChanged = cmpDecision?.decisionType != _consentDecisionType.value
        val purposesChanged = cmpDecision?.purposes != _selectedPurposes.value

        _hasUnsavedChanges.value = decisionTypeChanged || purposesChanged
    }

    /**
     * Save the current consent settings to the Tealium instance.
     */
    fun saveConsentSettings() {
        val decisionType = _consentDecisionType.value
            ?: return
        val purposes = _selectedPurposes.value
            ?: return

        cmp.setConsentDecision(ConsentDecision(decisionType, purposes))

        // Reset unsaved changes flag
        _hasUnsavedChanges.value = false
    }
}

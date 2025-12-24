package com.tealium.prism.mobile.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tealium.prism.mobile.databinding.FragmentTraceSettingsBinding
import com.tealium.prism.mobile.viewmodels.TraceFragmentViewModel
import kotlinx.coroutines.launch

class TraceFragment : Fragment() {
    private val viewModel: TraceFragmentViewModel by viewModels {
        TraceFragmentViewModel.provideFactory {
            requireActivity().getPreferences(Context.MODE_PRIVATE)
        }
    }

    private lateinit var binding: FragmentTraceSettingsBinding

    private lateinit var editTextTraceId: EditText
    private lateinit var buttonJoinTrace: Button
    private lateinit var buttonLeaveTrace: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTraceSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editTextTraceId = binding.editTextTraceId
        buttonJoinTrace = binding.btnJoinTrace
        buttonLeaveTrace = binding.btnLeaveTrace

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.traceId.collect { id ->
                    val current = binding.editTextTraceId.text?.toString() ?: ""
                    if (current != id) binding.editTextTraceId.setText(id)
                    binding.btnJoinTrace.isEnabled = id.isNotBlank()
                }
            }
        }

        editTextTraceId.doOnTextChanged { text, _, _, _ ->
            viewModel.onTraceIdUpdated(text.toString())
        }

        buttonJoinTrace.setOnClickListener {
            viewModel.joinTrace()
        }

        buttonLeaveTrace.setOnClickListener {
            editTextTraceId.text.clear()
            viewModel.leaveTrace()
        }
    }
}
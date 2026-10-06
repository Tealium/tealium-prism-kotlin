package com.tealium.prism.mobile.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tealium.prism.mobile.R
import com.tealium.prism.mobile.TealiumHelper
import com.tealium.prism.mobile.viewmodels.ContextApiFragmentViewModel
import com.tealium.prism.contextapi.EngineResponse
import kotlinx.coroutines.launch

class ContextApiFragment : Fragment() {

    private val viewModel: ContextApiFragmentViewModel by viewModels()

    private lateinit var editEngineId: EditText
    private lateinit var fetchButton: Button
    private lateinit var contextAttrListRecyclerView: RecyclerView
    private var adapter: ContextAttrListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_context_api, container, false)

        editEngineId = view.findViewById(R.id.edit_engine_id)
        fetchButton = view.findViewById(R.id.fetch_engine_response_button)
        contextAttrListRecyclerView = view.findViewById(R.id.context_attr_list)

        contextAttrListRecyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize with empty adapter to avoid "No adapter attached" warning
        adapter = ContextAttrListAdapter(emptyList())
        contextAttrListRecyclerView.adapter = adapter

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load saved engine ID
        context?.let {
            viewModel.loadSavedEngineId(it)
        }

        // Observe saved engine ID
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.savedEngineIdFlow.collect { savedEngineId ->
                    if (savedEngineId.isNotEmpty() && editEngineId.text.toString().isEmpty()) {
                        editEngineId.setText(savedEngineId)
                    }
                }
            }
        }

        // Save engine ID when text changes
        editEngineId.doAfterTextChanged { text ->
            context?.let {
                viewModel.saveEngineId(it, text?.toString() ?: "")
            }
        }

        fetchButton.setOnClickListener {
            val engineId = editEngineId.text.toString()
            TealiumHelper.track("fetch engine button click")
            viewModel.fetchEngineResponse(engineId)
        }

        // Observe engine response
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.engineResponseFlow.collect { response ->
                    parseAndUpdateUI(response)
                }
            }
        }

        // Observe errors
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorFlow.collect { errorMessage ->
                    showToast("Engine Response Error: $errorMessage")
                }
            }
        }
    }

    private fun parseAndUpdateUI(engineResponse: EngineResponse) {
        val responseList = mutableListOf<ContextDataEntry>()

        engineResponse.audiences?.let {
            responseList.add(ContextDataEntry("Audiences", it.toString()))
        }

        engineResponse.badges?.let {
            responseList.add(ContextDataEntry("Badges", it.toString()))
        }

        engineResponse.properties?.let {
            responseList.add(ContextDataEntry("Properties", it.toString()))
        }

        engineResponse.metrics?.let {
            responseList.add(ContextDataEntry("Metrics", it.toString()))
        }

        engineResponse.flags?.let {
            responseList.add(ContextDataEntry("Flags", it.toString()))
        }

        engineResponse.dates?.let {
            responseList.add(ContextDataEntry("Dates", it.toString()))
        }

        // Update adapter data
        adapter?.updateData(responseList.toList())
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private inner class ContextAttrListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contextLabelTextView = itemView.findViewById<TextView>(R.id.context_attr_name_label)
        val contextPlaceholderTextView =
            itemView.findViewById<TextView>(R.id.context_attr_text_placeholder)

        fun bind(label: String, text: String) {
            contextLabelTextView.text = label
            contextPlaceholderTextView.text = text
        }
    }

    private inner class ContextAttrListAdapter(private var contextData: List<ContextDataEntry>) :
        RecyclerView.Adapter<ContextAttrListViewHolder>() {

        fun updateData(newData: List<ContextDataEntry>) {
            contextData = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ContextAttrListViewHolder {
            val view = layoutInflater.inflate(R.layout.context_attr_list_item, parent, false)
            return ContextAttrListViewHolder(view)
        }

        override fun getItemCount(): Int {
            return contextData.size
        }

        override fun onBindViewHolder(holder: ContextAttrListViewHolder, position: Int) {
            val entry = contextData[position]
            holder.bind(entry.label, entry.text)
        }
    }

    private data class ContextDataEntry(val label: String, val text: String)
}


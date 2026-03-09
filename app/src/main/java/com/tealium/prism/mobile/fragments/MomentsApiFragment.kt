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
import com.tealium.prism.mobile.viewmodels.MomentsApiFragmentViewModel
import com.tealium.prism.momentsapi.EngineResponse
import kotlinx.coroutines.launch

class MomentsApiFragment : Fragment() {

    private val viewModel: MomentsApiFragmentViewModel by viewModels()

    private lateinit var editEngineId: EditText
    private lateinit var fetchButton: Button
    private lateinit var momentsAttrListRecyclerView: RecyclerView
    private var adapter: MomentsAttrListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_moments_api, container, false)

        editEngineId = view.findViewById(R.id.edit_engine_id)
        fetchButton = view.findViewById(R.id.fetch_engine_response_button)
        momentsAttrListRecyclerView = view.findViewById(R.id.moments_attr_list)

        momentsAttrListRecyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize with empty adapter to avoid "No adapter attached" warning
        adapter = MomentsAttrListAdapter(emptyList())
        momentsAttrListRecyclerView.adapter = adapter

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
        val responseList = mutableListOf<MomentsDataEntry>()

        engineResponse.audiences?.let {
            responseList.add(MomentsDataEntry("Audiences", it.toString()))
        }

        engineResponse.badges?.let {
            responseList.add(MomentsDataEntry("Badges", it.toString()))
        }

        engineResponse.properties?.let {
            responseList.add(MomentsDataEntry("Properties", it.toString()))
        }

        engineResponse.metrics?.let {
            responseList.add(MomentsDataEntry("Metrics", it.toString()))
        }

        engineResponse.flags?.let {
            responseList.add(MomentsDataEntry("Flags", it.toString()))
        }

        engineResponse.dates?.let {
            responseList.add(MomentsDataEntry("Dates", it.toString()))
        }

        // Update adapter data
        adapter?.updateData(responseList.toList())
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private inner class MomentsAttrListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val momentsLabelTextView = itemView.findViewById<TextView>(R.id.moments_attr_name_label)
        val momentsPlaceholderTextView =
            itemView.findViewById<TextView>(R.id.moments_attr_text_placeholder)

        fun bind(label: String, text: String) {
            momentsLabelTextView.text = label
            momentsPlaceholderTextView.text = text
        }
    }

    private inner class MomentsAttrListAdapter(private var momentsData: List<MomentsDataEntry>) :
        RecyclerView.Adapter<MomentsAttrListViewHolder>() {

        fun updateData(newData: List<MomentsDataEntry>) {
            momentsData = newData
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): MomentsAttrListViewHolder {
            val view = layoutInflater.inflate(R.layout.moments_attr_list_item, parent, false)
            return MomentsAttrListViewHolder(view)
        }

        override fun getItemCount(): Int {
            return momentsData.size
        }

        override fun onBindViewHolder(holder: MomentsAttrListViewHolder, position: Int) {
            val entry = momentsData[position]
            holder.bind(entry.label, entry.text)
        }
    }

    private data class MomentsDataEntry(val label: String, val text: String)
}


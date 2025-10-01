package com.tealium.prism.mobile.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tealium.prism.mobile.R
import com.tealium.prism.mobile.databinding.FragmentFeatureListBinding
import com.tealium.prism.mobile.features.Feature


class FeatureListFragment : Fragment() {

    interface FeatureSelectedListener {
        fun onFeatureSelected(feature: Feature)
    }

    private lateinit var binding: FragmentFeatureListBinding
    private var featureSelectedListener: FeatureSelectedListener? = null
    private lateinit var moduleListRecyclerView: RecyclerView
    private var adapter: FeatureListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFeatureListBinding.inflate(inflater, container, false)
        moduleListRecyclerView = binding.moduleListRecyclerView
        moduleListRecyclerView.layoutManager = LinearLayoutManager(context)

        updateUI()

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        featureSelectedListener = context as FeatureSelectedListener?
    }

    override fun onDetach() {
        super.onDetach()
        featureSelectedListener = null
    }

    private fun updateUI() {
        adapter = FeatureListAdapter(Feature.values().toList())
        moduleListRecyclerView.adapter = adapter
    }

    private inner class FeatureListViewHolder(view: View) : RecyclerView.ViewHolder(view),
        View.OnClickListener {
        private val featureNameButton = itemView.findViewById<Button>(R.id.feature_name_button)

        private lateinit var feature: Feature

        init {
            featureNameButton.setOnClickListener(this)
        }

        fun bind(feature: Feature) {
            this.feature = feature
            featureNameButton.text = feature.featureName
        }

        override fun onClick(v: View?) {
            featureSelectedListener?.onFeatureSelected(feature)
        }
    }

    private inner class FeatureListAdapter(private val features: List<Feature>) :
        RecyclerView.Adapter<FeatureListViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureListViewHolder {
            val view = layoutInflater.inflate(R.layout.list_item_feature, parent, false)
            return FeatureListViewHolder(view)
        }

        override fun getItemCount(): Int {
            return features.count()
        }

        override fun onBindViewHolder(holder: FeatureListViewHolder, position: Int) {
            val moduleName = features[position]
            holder.bind(moduleName)
        }
    }
}
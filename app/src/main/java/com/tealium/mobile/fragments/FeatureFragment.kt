package com.tealium.mobile.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tealium.mobile.R
import com.tealium.mobile.databinding.FragmentFeatureBinding
import com.tealium.mobile.features.Feature
import com.tealium.mobile.features.FeatureAction


abstract class BaseFeatureFragment(
    feature: Feature
) : Fragment() {

    protected lateinit var binding: FragmentFeatureBinding
    private lateinit var featureActionsRecyclerView: RecyclerView
    private var adapter: FeatureActionAdapter? = null

    val title: String = feature.featureName
    abstract val actions : List<FeatureAction>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFeatureBinding.inflate(inflater, container, false)

        binding.textViewFeatureTitle.text = title
        binding.buttonBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        featureActionsRecyclerView = binding.featureActionRecyclerView
        featureActionsRecyclerView.layoutManager = LinearLayoutManager(context)

        adapter = FeatureActionAdapter(actions)
        featureActionsRecyclerView.adapter = adapter

        return binding.root
    }

    private inner class FeatureListViewHolder(view: View) : RecyclerView.ViewHolder(view),
        View.OnClickListener {
        private val featureNameButton = itemView.findViewById<Button>(R.id.feature_name_button)

        private lateinit var feature: FeatureAction

        init {
            featureNameButton.setOnClickListener(this)
        }

        fun bind(feature: FeatureAction) {
            this.feature = feature
            featureNameButton.text = feature.title
        }

        override fun onClick(v: View?) {
            feature.action.invoke()
        }
    }

    private inner class FeatureActionAdapter(private val features: List<FeatureAction>) :
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
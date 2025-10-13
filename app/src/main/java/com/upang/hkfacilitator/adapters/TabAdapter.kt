package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemTabBinding
import com.upang.hkfacilitator.viewholders.TabViewHolder

class TabAdapter(
    private val layouts: List<View>
) :
    RecyclerView.Adapter<TabViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemTabBinding.inflate(from, parent, false)
        return TabViewHolder(binding)
    }

    override fun getItemCount(): Int = layouts.size

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bindItem(layouts[position])
    }
}

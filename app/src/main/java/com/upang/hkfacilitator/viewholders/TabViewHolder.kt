package com.upang.hkfacilitator.viewholders

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemTabBinding

class TabViewHolder(
    private var binding: ItemTabBinding
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindItem(rv: View) {
        binding.pageContainer.addView(rv)
    }
}

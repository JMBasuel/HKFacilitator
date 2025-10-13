package com.upang.hkfacilitator.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemFaciReqBinding
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.FaciReqClickListener

class FaciReqViewHolder(
    private var binding: ItemFaciReqBinding,
    private val faciReqClickListener: FaciReqClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindUrls(url: String, isAddNew: Boolean) {
        if (!isAddNew) Picasso.get()
            .load(url)
            .placeholder(R.drawable.empty)
            .into(binding.reqImg)
        else binding.reqImg.setImageResource(R.drawable.add)
        binding.cvReq.setOnDebouncedClickListener {
            if (isAddNew) faciReqClickListener.onAddReqClick()
            else faciReqClickListener.onReqClick(url)
        }
    }
}

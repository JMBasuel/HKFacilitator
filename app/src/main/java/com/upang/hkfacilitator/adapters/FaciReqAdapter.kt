package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemFaciReqBinding
import com.upang.hkfacilitator.utils.FaciReqClickListener
import com.upang.hkfacilitator.viewholders.FaciReqViewHolder

class FaciReqAdapter(
    private val urls: ArrayList<String>,
    private val faciReqClickListener: FaciReqClickListener
) :
    RecyclerView.Adapter<FaciReqViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaciReqViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemFaciReqBinding.inflate(from, parent, false)
        return FaciReqViewHolder(binding, faciReqClickListener)
    }

    override fun getItemCount(): Int = urls.size

    override fun onBindViewHolder(holder: FaciReqViewHolder, position: Int) {
        holder.bindUrls(urls[position], urls[position].isEmpty())
    }
}

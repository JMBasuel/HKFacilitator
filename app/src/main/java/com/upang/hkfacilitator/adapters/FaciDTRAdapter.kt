package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemFaciDtrBinding
import com.upang.hkfacilitator.models.Schedule
import com.upang.hkfacilitator.utils.DTRClickListener
import com.upang.hkfacilitator.viewholders.FaciDTRViewHolder

class FaciDTRAdapter(
    private val schedules: ArrayList<Schedule>,
    private val clickListener: DTRClickListener,
    private val isAdmin: Boolean
) :
    RecyclerView.Adapter<FaciDTRViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FaciDTRViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemFaciDtrBinding.inflate(from, parent, false)
        return FaciDTRViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = schedules.size

    override fun onBindViewHolder(holder: FaciDTRViewHolder, position: Int) {
        holder.bindSchedule(schedules[position], isAdmin)
    }
}
package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemExtraSchedBinding
import com.upang.hkfacilitator.models.Schedule
import com.upang.hkfacilitator.utils.SchedClickListener
import com.upang.hkfacilitator.viewholders.SchedViewHolder
import java.util.ArrayList

class SchedAdapter(
    private val sched: ArrayList<Schedule>,
    private val clickListener: SchedClickListener
) :
    RecyclerView.Adapter<SchedViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SchedViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemExtraSchedBinding.inflate(from, parent, false)
        return SchedViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = sched.size

    override fun onBindViewHolder(holder: SchedViewHolder, position: Int) {
        holder.bindSched(sched[position])
    }
}

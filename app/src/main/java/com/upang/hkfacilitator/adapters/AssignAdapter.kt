package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemAssignBinding
import com.upang.hkfacilitator.models.Schedule
import com.upang.hkfacilitator.utils.AssignClickListener
import com.upang.hkfacilitator.viewholders.AssignViewHolder
import java.util.ArrayList

class AssignAdapter(
    private val sched: ArrayList<Schedule>,
    private val clickListener: AssignClickListener
) :
    RecyclerView.Adapter<AssignViewHolder>()
{
    private val viewHolders = mutableListOf<AssignViewHolder?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssignViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemAssignBinding.inflate(from, parent, false)
        val viewHolder = AssignViewHolder(binding, clickListener)
        viewHolders.add(viewHolder)
        return viewHolder
    }

    override fun getItemCount(): Int = sched.size

    override fun onBindViewHolder(holder: AssignViewHolder, position: Int) {
        holder.bindSched(sched[position])
    }

    fun getAssignViewHolders(id: String): List<AssignViewHolder> {
        val validViewHolders = mutableListOf<AssignViewHolder>()
        for (viewHolder in viewHolders) if (viewHolder != null && viewHolder.id == id)
            validViewHolders.add(viewHolder)
        return validViewHolders
    }
}

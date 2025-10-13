package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemScheduleFaciBatchedBinding
import com.upang.hkfacilitator.models.User
import com.upang.hkfacilitator.utils.SchedFaciBatchedClickListener
import com.upang.hkfacilitator.viewholders.SchedFaciBatchedViewHolder

class SchedFaciBatchedAdapter(
    private val faciUsers: ArrayList<User>,
    private val clickListener: SchedFaciBatchedClickListener
) :
    RecyclerView.Adapter<SchedFaciBatchedViewHolder>()
{
    private val viewHolders = mutableListOf<SchedFaciBatchedViewHolder?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SchedFaciBatchedViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemScheduleFaciBatchedBinding.inflate(from, parent, false)
        val viewHolder = SchedFaciBatchedViewHolder(binding, clickListener)
        viewHolders.add(viewHolder)
        return viewHolder
    }

    override fun onBindViewHolder(holder: SchedFaciBatchedViewHolder, position: Int) {
        holder.bindScheduleFaci(faciUsers[position])
    }

    override fun getItemCount(): Int = faciUsers.size

    fun getViewHolders(): List<SchedFaciBatchedViewHolder> {
        val validViewHolders = mutableListOf<SchedFaciBatchedViewHolder>()
        for (viewHolder in viewHolders) if (viewHolder != null)
            validViewHolders.add(viewHolder)
        return validViewHolders
    }
}
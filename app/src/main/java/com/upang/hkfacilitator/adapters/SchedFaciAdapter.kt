package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemScheduleFaciBinding
import com.upang.hkfacilitator.models.User
import com.upang.hkfacilitator.utils.SchedFaciClickListener
import com.upang.hkfacilitator.viewholders.SchedFaciViewHolder
import java.util.ArrayList

class SchedFaciAdapter(
    private val faciUsers: ArrayList<User>,
    private val clickListener: SchedFaciClickListener,
    private val isFinish: Boolean,
    private val isFaculty: Boolean,
    private val isActiveOrDone: Boolean
) :
    RecyclerView.Adapter<SchedFaciViewHolder>()
{
    private val viewHolders = mutableListOf<SchedFaciViewHolder?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SchedFaciViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemScheduleFaciBinding.inflate(from, parent, false)
        val viewHolder = SchedFaciViewHolder(binding, clickListener)
        viewHolders.add(viewHolder)
        return viewHolder
    }

    override fun onBindViewHolder(holder: SchedFaciViewHolder, position: Int) {
        holder.bindScheduleFaci(faciUsers[position], isFinish, isFaculty, isActiveOrDone)
    }

    override fun getItemCount(): Int = faciUsers.size

    fun getViewHolders(): List<SchedFaciViewHolder> {
        val validViewHolders = mutableListOf<SchedFaciViewHolder>()
        for (viewHolder in viewHolders) if (viewHolder != null)
            validViewHolders.add(viewHolder)
        return validViewHolders
    }
}

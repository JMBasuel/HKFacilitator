package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemAssignBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.utils.AssignClickListener
import androidx.core.graphics.toColorInt

@SuppressLint("SetTextI18n")
class AssignViewHolder(
    var binding: ItemAssignBinding,
    private val clickListener: AssignClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    lateinit var id: String

    fun bindSched(sched: Schedule) {
        id = sched.id!!
        binding.title.text = sched.title
        binding.owner.text = sched.owner
        binding.room.text = sched.room
        binding.date.text = sched.date
        binding.time.text = timeRangeTo12(sched.time)
        binding.faci.text = "${sched.joined}/${sched.need}"
        binding.cvSched.setOnDebouncedClickListener {
            if (Global.assignPrev != null)
                resetCard(Global.assignPrev!!)
            Global.assignPrev = id
            clickListener.onSchedClick(sched, binding)
        }
    }

    private fun resetCard(id: String) {
        val previous = clickListener.getViewHolder(id)
        for (viewHolder in previous)
            viewHolder.binding.cvSched.setCardBackgroundColor("#FFFFFF".toColorInt())
    }
}

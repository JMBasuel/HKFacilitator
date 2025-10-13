package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemExtraSchedBinding
import com.upang.hkfacilitator.models.Global.getWeekRange
import com.upang.hkfacilitator.models.Global.isPast
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Global.todayDay
import com.upang.hkfacilitator.models.Schedule
import com.upang.hkfacilitator.utils.SchedClickListener

@SuppressLint("SetTextI18n")
class SchedViewHolder(
    private var binding: ItemExtraSchedBinding,
    private val clickListener: SchedClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindSched(sched: Schedule) {
        binding.title.text = sched.title
        binding.room.text = sched.room
        binding.date.text = if (sched.isDone == true && !sched.date!!.contains('-') && sched.date != todayDay()) {
            val (start, _) = getWeekRange(sched.date!!)
            start
        } else sched.date
        binding.time.text = timeRangeTo12(sched.time)
        if (sched.joined != null && sched.need != null)
            binding.faci.text = "${sched.joined}/${sched.need}"
        else binding.faci.visibility = View.GONE
        binding.cvSchedule.setOnDebouncedClickListener {
            clickListener.onSchedClick(sched)
        }
        if (sched.extension == true) binding.extendNotice.visibility = View.VISIBLE
        if (!sched.id!![1].isLetter() && isPast(sched, true) && sched.joined!! == 0)
            binding.expiredNotice.visibility = View.VISIBLE
        else if (sched.suspended == true) binding.suspendNotice.visibility = View.VISIBLE
    }
}

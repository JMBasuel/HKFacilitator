package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemVacantBinding
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Timestamp
import com.upang.hkfacilitator.utils.TimeClickListener

@SuppressLint("SetTextI18n")
class VacantViewHolder(
    private var binding: ItemVacantBinding,
    private val clickListener: TimeClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindVacant(vacant: Timestamp, isAdmin: Boolean) {
        binding.day.text = vacant.time!!.substringBefore('.')
        binding.time.text = timeRangeTo12(vacant.time.substringAfter('.'))
        if (!isAdmin) {
            binding.delete.visibility = View.VISIBLE
            binding.delete.setOnDebouncedClickListener {
                clickListener.onTimeDeleteClick(vacant.id!!)
            }
        }
    }
}

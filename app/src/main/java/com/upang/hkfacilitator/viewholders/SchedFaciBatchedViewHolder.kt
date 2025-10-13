package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemScheduleFaciBatchedBinding
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.timeTo12
import com.upang.hkfacilitator.utils.SchedFaciBatchedClickListener

@SuppressLint("SetTextI18n")
class SchedFaciBatchedViewHolder(
    var binding: ItemScheduleFaciBatchedBinding,
    private val clickListener: SchedFaciBatchedClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    lateinit var user: User

    fun bindScheduleFaci(user: User) {
        this.user = user
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.image.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(user.profileUrl)
            .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.image)
        binding.name.text = user.name
        val year = user.course!!.removeCourse()
        binding.course.text = "${
            Global.courses?.filter { it.contains(user.course!!.removeYear()) }
                ?.map{ it.substringAfterLast('.') }?.get(0)} $year"
        binding.timeIn.text = timeTo12(user.timeIn)
        binding.timeOut.text = timeTo12(user.timeOut)
        binding.cvFacilitator.setOnClickListener {
            clickListener.onRadioSelect()
            toggleRadio()
        }
        binding.mark.setOnCheckedChangeListener { _, _ ->
            clickListener.onRadioSelect()
        }
    }

    private fun toggleRadio() {
        when (binding.mark.checkedRadioButtonId) {
            R.id.present -> {
                binding.absent.isChecked = true
                binding.state.apply {
                    text = "ABSENT"
                    setTextColor(itemView.context.getColor(R.color.red))
                }
            }
            else -> {
                binding.present.isChecked = true
                binding.state.apply {
                    text = "PRESENT"
                    setTextColor(itemView.context.getColor(R.color.green_dark))
                }
            }
        }
    }
}
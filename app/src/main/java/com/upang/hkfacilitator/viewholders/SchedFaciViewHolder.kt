package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemScheduleFaciBinding
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.timeTo12
import com.upang.hkfacilitator.utils.SchedFaciClickListener

@SuppressLint("SetTextI18n")
class SchedFaciViewHolder(
    var binding: ItemScheduleFaciBinding,
    private val clickListener: SchedFaciClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    lateinit var user: User

    fun bindScheduleFaci(user: User, isFinish: Boolean, isFaculty: Boolean, isActiveOrDone: Boolean) {
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
        if (isActiveOrDone) {
            binding.time.visibility = View.VISIBLE
            binding.timeIn.text = timeTo12(user.timeIn)
            binding.timeOut.text = timeTo12(user.timeOut)
            if (isFinish) {
                binding.timeIn.isEnabled = false
                binding.timeOut.isEnabled = false
            }
            if (isFaculty) {
                binding.timeIn.setOnDebouncedClickListener {
                    clickListener.onTimeClick(user, true)
                }
                binding.timeOut.setOnDebouncedClickListener {
                    clickListener.onTimeClick(user, false)
                }
            }
        }
        if (isFinish && isFaculty) {
            binding.checkBox.visibility = View.VISIBLE
            binding.cvFacilitator.setOnClickListener {
                binding.checkBox.isChecked = !binding.checkBox.isChecked
            }
            binding.checkBox.setOnCheckedChangeListener { _, _ ->
                clickListener.onCheckClick()
            }
        }
    }
}

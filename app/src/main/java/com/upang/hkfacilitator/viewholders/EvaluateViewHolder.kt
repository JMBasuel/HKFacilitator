package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemFacultyBinding
import com.upang.hkfacilitator.models.Global.removeCourse
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.FacultyClickListener

@SuppressLint("SetTextI18n")
class EvaluateViewHolder(
    private var binding: ItemFacultyBinding,
    private val clickListener: FacultyClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindUser(user: User) {
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.image.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(user.profileUrl)
            .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.image)
        binding.name.text = user.name
        if (user.course != null) {
            val year = user.course!!.removeCourse()
            binding.course.apply {
                text = "${Global.courses?.filter { it.contains( user.course!!.removeYear()) }
                    ?.map{ it.substringAfterLast('.') }?.get(0)} $year"
                visibility = View.VISIBLE
            }
        }
        binding.cvFaculty.setOnDebouncedClickListener {
            clickListener.onFacultyClick(user)
        }
    }
}
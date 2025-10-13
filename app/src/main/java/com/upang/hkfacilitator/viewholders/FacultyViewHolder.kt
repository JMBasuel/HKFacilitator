package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemFacultyBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.removeYear
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.FacultyClickListener

@SuppressLint("SetTextI18n")
class FacultyViewHolder(
    private var binding: ItemFacultyBinding,
    private val clickListener: FacultyClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindUser(user: User, isFaculty: Boolean) {
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.image.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(user.profileUrl)
            .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.image)
        binding.name.text = user.name
        if (isFaculty) {
            if (user.extension == true) binding.extensionNotice.visibility = View.VISIBLE
            binding.cvFaculty.setOnDebouncedClickListener {
                clickListener.onFacultyClick(user)
            }
        } else {
            binding.course.text = "${Global.courses?.filter { it.contains(user.course!!
                .removeYear()) }?.map { it.substringAfterLast('.') }?.get(0)}"
            binding.course.visibility = View.VISIBLE
            binding.cvFaculty.setOnDebouncedClickListener {
                clickListener.onManagerClick(user)
            }
        }
    }
}
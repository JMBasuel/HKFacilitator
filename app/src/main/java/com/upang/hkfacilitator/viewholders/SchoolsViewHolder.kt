package com.upang.hkfacilitator.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemSchoolBinding
import com.upang.hkfacilitator.models.School
import com.upang.hkfacilitator.utils.SchoolClickListener

class SchoolsViewHolder(
    private val binding: ItemSchoolBinding,
    private val clickListener: SchoolClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindSchool(school: School) {
        Picasso.get()
            .load(school.icon)
            .placeholder(R.drawable.empty)
            .into(binding.schoolImg)
        binding.schoolName.text = school.name
        binding.cvSchool.setOnClickListener {
            clickListener.onSchoolClick(school.name!!)
        }
    }
}
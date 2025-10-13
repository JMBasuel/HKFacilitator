package com.upang.hkfacilitator.viewholders

import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemCourseBinding
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.CourseClickListener

class CourseViewHolder(
    private var binding: ItemCourseBinding,
    private val clickListener: CourseClickListener
)
    : RecyclerView.ViewHolder(binding.root)
{
    fun bindCourse(course: String) {
        binding.course.text = course.substringAfterLast('.')
        binding.delete.setOnDebouncedClickListener {
            clickListener.onDeleteClick(course)
        }
    }
}
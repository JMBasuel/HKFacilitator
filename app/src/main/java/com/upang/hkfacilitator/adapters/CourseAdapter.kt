package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemCourseBinding
import com.upang.hkfacilitator.utils.CourseClickListener
import com.upang.hkfacilitator.viewholders.CourseViewHolder

class CourseAdapter(
    private val courses: ArrayList<String>,
    private val clickListener: CourseClickListener
)
    : RecyclerView.Adapter<CourseViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemCourseBinding.inflate(from, parent, false)
        return CourseViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        holder.bindCourse(courses[position])
    }

    override fun getItemCount(): Int = courses.size

}
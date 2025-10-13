package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemSchoolBinding
import com.upang.hkfacilitator.models.School
import com.upang.hkfacilitator.utils.SchoolClickListener
import com.upang.hkfacilitator.viewholders.SchoolsViewHolder

class SchoolsAdapter(
    private val schools: ArrayList<School>,
    private val clickListener: SchoolClickListener
) :
    RecyclerView.Adapter<SchoolsViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SchoolsViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemSchoolBinding.inflate(from, parent, false)
        return SchoolsViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = schools.size

    override fun onBindViewHolder(holder: SchoolsViewHolder, position: Int) {
        holder.bindSchool(schools[position])
    }
}
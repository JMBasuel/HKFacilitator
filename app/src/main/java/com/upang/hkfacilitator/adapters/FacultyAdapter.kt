package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemFacultyBinding
import com.upang.hkfacilitator.models.User
import com.upang.hkfacilitator.utils.FacultyClickListener
import com.upang.hkfacilitator.viewholders.FacultyViewHolder

class FacultyAdapter(
    private val users : List<User>,
    private val clickListener: FacultyClickListener,
    private val isFaculty: Boolean
) :
    RecyclerView.Adapter<FacultyViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacultyViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemFacultyBinding.inflate(from, parent, false)
        return FacultyViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = users.size

    override fun onBindViewHolder(holder: FacultyViewHolder, position: Int) {
        holder.bindUser(users[position], isFaculty)
    }

}
package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemFacultyBinding
import com.upang.hkfacilitator.models.User
import com.upang.hkfacilitator.utils.FacultyClickListener
import com.upang.hkfacilitator.viewholders.EvaluateViewHolder

class EvaluateAdapter(
    private val users: ArrayList<User>,
    private val clickListener: FacultyClickListener
) :
    RecyclerView.Adapter<EvaluateViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EvaluateViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemFacultyBinding.inflate(from, parent, false)
        return EvaluateViewHolder(binding, clickListener)
    }

    override fun onBindViewHolder(holder: EvaluateViewHolder, position: Int) {
        holder.bindUser(users[position])
    }

    override fun getItemCount(): Int = users.size
}
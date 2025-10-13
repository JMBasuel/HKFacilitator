package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemFacilitatorBinding
import com.upang.hkfacilitator.models.User
import com.upang.hkfacilitator.utils.FacilitatorClickListener
import com.upang.hkfacilitator.viewholders.FacilitatorViewHolder

class FacilitatorAdapter(
    private val users : ArrayList<User>,
    private val clickListener: FacilitatorClickListener
) :
    RecyclerView.Adapter<FacilitatorViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FacilitatorViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemFacilitatorBinding.inflate(from, parent, false)
        return FacilitatorViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = users.size

    override fun onBindViewHolder(holder: FacilitatorViewHolder, position: Int) {
        holder.bindUser(users[position])
    }

}
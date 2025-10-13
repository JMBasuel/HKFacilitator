package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemVacantBinding
import com.upang.hkfacilitator.models.Timestamp
import com.upang.hkfacilitator.utils.TimeClickListener
import com.upang.hkfacilitator.viewholders.VacantViewHolder
import java.util.ArrayList

class VacantAdapter(
    private val vacant: ArrayList<Timestamp>,
    private val clickListener: TimeClickListener,
    private val isAdmin: Boolean
) :
    RecyclerView.Adapter<VacantViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VacantViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemVacantBinding.inflate(from, parent, false)
        return VacantViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = vacant.size

    override fun onBindViewHolder(holder: VacantViewHolder, position: Int) {
        holder.bindVacant(vacant[position], isAdmin)
    }
}

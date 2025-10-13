package com.upang.hkfacilitator.adapters

import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemNotificationBinding
import com.upang.hkfacilitator.models.Notifications
import com.upang.hkfacilitator.utils.NotificationClickListener
import com.upang.hkfacilitator.viewholders.NotificationViewHolder

class NotificationAdapter(
    private val notifications: ArrayList<Notifications>,
    private val clickListener: NotificationClickListener
) :
    RecyclerView.Adapter<NotificationViewHolder>()
{
    private val viewHolders = mutableListOf<NotificationViewHolder?>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemNotificationBinding.inflate(from, parent, false)
        val viewHolder = NotificationViewHolder(parent.context, binding, clickListener)
        viewHolders.add(viewHolder)
        return viewHolder
    }

    override fun getItemCount(): Int = notifications.size

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bindNotification(notifications[position])
    }

    fun getNotificationViewHolders(id: String): List<NotificationViewHolder> {
        val validViewHolders = mutableListOf<NotificationViewHolder>()
        for (viewHolder in viewHolders) if (viewHolder != null && viewHolder.id == id)
            validViewHolders.add(viewHolder)
        return validViewHolders
    }
}
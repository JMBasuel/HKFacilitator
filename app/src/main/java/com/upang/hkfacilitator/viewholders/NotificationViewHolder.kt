package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.content.Context
import android.view.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemNotificationBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.timeTo12
import com.upang.hkfacilitator.utils.NotificationClickListener
import androidx.core.view.isVisible

@SuppressLint("SetTextI18n")
class NotificationViewHolder(
    private val context: Context,
    var binding: ItemNotificationBinding,
    private val clickListener: NotificationClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    lateinit var id: String
    private val black = ContextCompat.getColor(context, R.color.black)
    private val gray = ContextCompat.getColor(context, R.color.gray)

    fun bindNotification(notification: Notifications) {
        if (notification.read == null) {
            binding.sender.setTextColor(black)
            binding.datetime.setTextColor(black)
        }
        id = notification.id!!
        binding.sender.text = "From: ${
            if (id.endsWith("AD") || id.endsWith("REQUIREMENT")) "Admin"
            else if (id.endsWith("MA")) "Manager" else "Faculty"}"
        binding.datetime.text = "${notification.datetime!!
            .substringBefore(" ")} ${timeTo12(notification.datetime
                .substringAfter(" "))}"
        binding.title.text = notification.title
        binding.message.text = notification.message
        binding.cvNotification.setOnClickListener {
            if (notification.read == null) {
                notification.read = true
                clickListener.readNotification(id)
                binding.sender.setTextColor(gray)
                binding.datetime.setTextColor(gray)
            }
            if (Global.notificationPrev == id) {
                if (binding.content.isVisible) binding.content.visibility = View.GONE
                else {
                    viewLinks(notification)
                    binding.content.visibility = View.VISIBLE
                }
            } else {
                if (Global.notificationPrev != null) resetCard(Global.notificationPrev!!)
                viewLinks(notification)
                binding.content.visibility = View.VISIBLE
                Global.notificationPrev = id
            }
        }
    }

    private fun viewLinks(notification: Notifications) {
        if (notification.link != null) {
            if (notification.link.startsWith("HOME") || notification.link.startsWith("SCHEDULE.")) {
                if (notification.link.startsWith("HOMEREQ")) {
                    Global.homeTab = 1
                    binding.btnLink.text = "Upload now"
                }
                binding.btnLink.setOnDebouncedClickListener {
                    clickListener.navigateToLink(notification.link.substringAfter("."),
                        if (notification.link.startsWith("HOME")) "HOME" else "SCHEDULE")
                }
                binding.btnLink.visibility = View.VISIBLE
            } else {
                binding.buttons.removeAllViews()
                for (idOwner in notification.link.substringAfter(".").split(".")) {
                    if (idOwner.isNotEmpty()) {
                        val link = MaterialButton(context).apply {
                            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                resources.getDimensionPixelSize(R.dimen.size_30dp)).apply {
                                setMargins(0, 1, 0, 1) }
                            setPadding(0, 0, 0, 0)
                            text = idOwner.substringAfter(",")
                            textSize = 11f
                            setTextColor(ContextCompat.getColor(context, R.color.white))
                            cornerRadius = 1
                            setBackgroundColor(ContextCompat.getColor(context, R.color.button_tint_green))
                        }
                        link.setOnDebouncedClickListener {
                            clickListener.navigateToLink(idOwner.substringBefore(","), "SCHEDULE")
                        }
                        binding.buttons.addView(link)
                    }
                }
                binding.buttons.visibility = View.VISIBLE
            }
        }
    }

    private fun resetCard(id: String) {
        val previous = clickListener.getViewHolder(id)
        for (viewHolder in previous)
            viewHolder.binding.content.visibility = View.GONE
    }
}
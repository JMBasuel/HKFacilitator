package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemFacilitatorBinding
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.User
import com.upang.hkfacilitator.utils.FacilitatorClickListener

@SuppressLint("SetTextI18n")
class FacilitatorViewHolder(
    private var binding : ItemFacilitatorBinding,
    private val clickListener: FacilitatorClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindUser(user : User) {
        if (user.profileUrl == null) {
            if (user.gender.equals("Female"))
                binding.image.setImageResource(R.drawable.head_female)
        } else Picasso.get()
            .load(user.profileUrl)
            .placeholder(if (user.gender.equals("Male")) R.drawable.head_male
            else R.drawable.head_female)
            .into(binding.image)
        if (user.appeals != null) binding.appealNotice.visibility = View.VISIBLE
        binding.name.text = user.name
        binding.course.text = user.course
        binding.hours.text = "${"%.1f".format(user.hrs)}/${"%.1f".format(user.render)} HRS"
        binding.cvFacilitator.setOnDebouncedClickListener {
            clickListener.onFacilitatorClick(user)
        }
    }
}
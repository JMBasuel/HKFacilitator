package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.net.Uri
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.R
import com.upang.hkfacilitator.databinding.ItemFaciDtrBinding
import com.upang.hkfacilitator.models.*
import com.upang.hkfacilitator.models.Global.isDayAfter
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.models.Global.timeRangeTo12
import com.upang.hkfacilitator.models.Global.timeTo12
import com.upang.hkfacilitator.utils.DTRClickListener
import androidx.core.graphics.toColorInt

@SuppressLint("SetTextI18n")
class FaciDTRViewHolder(
    private var binding: ItemFaciDtrBinding,
    private val clickListener: DTRClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    private var uri: Uri? = null
    private var appeal: String? = null

    fun bindSchedule(sched : Schedule, isAdmin: Boolean) {
        binding.title.text = sched.title
        binding.owner.text = sched.owner
        binding.date.text = sched.date
        binding.time.text = timeRangeTo12(sched.time)
        binding.remark.text = sched.remark
        binding.hours.text = "${"%.1f".format(sched.hours)} HRS"
        if (sched.title!!.endsWith("EXT")) {
            binding.timeIn.visibility = View.GONE
            binding.timeOut.visibility = View.GONE
        } else {
            binding.timeIn.text = "Time-in: ${timeTo12(sched.timeIn)}"
            binding.timeOut.text = "Time-out: ${timeTo12(sched.timeOut)}"
        }
        if (sched.remark.equals("ABSENT")) {
            binding.cvFacilitator.setCardBackgroundColor("#33CC0000".toColorInt())
            binding.appeal.visibility = View.VISIBLE
            if (sched.appeal.isNullOrBlank()) binding.appealMessage.text = "NO APPEAL"
            else binding.appealMessage.text = sched.appeal
            if (sched.proof != null) {
                Picasso.get()
                    .load(sched.proof)
                    .placeholder(R.drawable.empty)
                    .into(binding.proof)
                binding.proof.visibility = View.VISIBLE
            }
            uri = Global.proofUri[sched.id]
            appeal = Global.appeals[sched.id]
            if (uri != null) binding.proof.setImageURI(uri)
            if (appeal != null) binding.editAppeal.setText(appeal)
            if (sched.approved == null) {
                if (!isAdmin) {
                    binding.proof.visibility = View.VISIBLE
                    if (sched.appeal == null) {
                        if (isDayAfter(sched.date!!, true, 2)) {
                            binding.appealMessage.visibility = View.GONE
                            binding.editAppeal.visibility = View.VISIBLE
                            binding.proof.setOnDebouncedClickListener {
                                clickListener.onAddProofClick { uri ->
                                    Global.proofUri[sched.id!!] = uri
                                    Global.appeals[sched.id!!] = if (binding.editAppeal.text.isNotEmpty())
                                        binding.editAppeal.text.toString() else null
                                    binding.proof.setImageURI(uri)
                                }
                            }
                            binding.btnNo.setOnDebouncedClickListener {
                                binding.editAppeal.text = null
                            }
                            binding.btnYes.setOnDebouncedClickListener {
                                if (!binding.editAppeal.text.isNullOrBlank()) {
                                    sched.appeal = binding.editAppeal.text.toString()
                                    clickListener.onBtnClick(sched, uri)
                                } else binding.editAppeal.error = "Required"
                            }
                        } else {
                            binding.proof.visibility = View.GONE
                            binding.buttons.visibility = View.GONE
                        }
                    } else {
                        if (sched.proof != null) binding.proof.setOnDebouncedClickListener {
                            clickListener.onViewProofClick(sched.proof!!)
                        }
                        swap2(sched.appeal!!)
                    }
                } else {
                    binding.proof.setOnDebouncedClickListener {
                        clickListener.onViewProofClick(sched.proof!!)
                    }
                    if (sched.appeal.isNullOrBlank()) binding.buttons.visibility = View.GONE
                    binding.btnNo.setOnDebouncedClickListener {
                        sched.approved = false
                        clickListener.onBtnClick(sched, null)
                    }
                    binding.btnYes.setOnDebouncedClickListener {
                        sched.approved = true
                        clickListener.onBtnClick(sched, null)
                    }
                }
            } else {
                binding.proof.setOnDebouncedClickListener {
                    clickListener.onViewProofClick(sched.proof!!)
                }
                swap1(if (sched.approved!!) "APPROVED" else "NOT APPROVED")
            }
        }
    }

    private fun swap1(feedback: String) {
        binding.feedback.text = feedback
        binding.buttons.visibility = View.GONE
        binding.feedback.visibility = View.VISIBLE
    }

    private fun swap2(appeal: String) {
        binding.appealMessage.text = appeal
        binding.feedback.text = "NO FEEDBACK"
        binding.editAppeal.visibility = View.GONE
        binding.buttons.visibility = View.GONE
        binding.appealMessage.visibility = View.VISIBLE
        binding.feedback.visibility = View.VISIBLE
    }
}
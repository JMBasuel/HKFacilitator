package com.upang.hkfacilitator.viewholders

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemEvaluationBinding
import com.upang.hkfacilitator.models.Evaluation
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import com.upang.hkfacilitator.utils.EvaluationClickListener

@SuppressLint("SetTextI18n")
class EvaluationViewHolder(
    private var binding: ItemEvaluationBinding,
    private val clickListener: EvaluationClickListener
) :
    RecyclerView.ViewHolder(binding.root)
{
    fun bindEval(eval: Evaluation, isAdmin: Boolean) {
        binding.name.text = eval.name
        binding.date.text = "${eval.date!!.substringAfter("-")}-${eval.date.substringBefore("-")}"
        binding.message.text = eval.comment
        if (!isAdmin) {
            binding.buttons.visibility = View.VISIBLE
            binding.btnEdit.setOnDebouncedClickListener {
                clickListener.onEvalEditClick(eval)
            }
            binding.btnDelete.setOnDebouncedClickListener {
                clickListener.onEvalDeleteClick(eval.id!!)
            }
        }
    }
}

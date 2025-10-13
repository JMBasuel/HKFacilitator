package com.upang.hkfacilitator.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.upang.hkfacilitator.databinding.ItemEvaluationBinding
import com.upang.hkfacilitator.models.Evaluation
import com.upang.hkfacilitator.utils.EvaluationClickListener
import com.upang.hkfacilitator.viewholders.EvaluationViewHolder
import java.util.ArrayList

class EvaluationAdapter(
    private val eval: ArrayList<Evaluation>,
    private val clickListener: EvaluationClickListener,
    private val isAdmin: Boolean
) :
    RecyclerView.Adapter<EvaluationViewHolder>()
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EvaluationViewHolder {
        val from = LayoutInflater.from(parent.context)
        val binding = ItemEvaluationBinding.inflate(from, parent, false)
        return EvaluationViewHolder(binding, clickListener)
    }

    override fun getItemCount(): Int = eval.size

    override fun onBindViewHolder(holder: EvaluationViewHolder, position: Int) {
        holder.bindEval(eval[position], isAdmin)
    }
}

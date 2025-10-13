package com.upang.hkfacilitator.ui.popup

import android.annotation.SuppressLint
import android.app.*
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import com.squareup.picasso.Picasso
import com.upang.hkfacilitator.databinding.DialogImageBinding
import com.upang.hkfacilitator.models.Global.setOnDebouncedClickListener
import androidx.core.graphics.drawable.toDrawable

@SuppressLint("SetTextI18n")
class ImageDialog(
    private val title: String,
    private val url: String,
    private val isAdmin: Boolean,
    private val onEditClick: (() -> Unit?)?
)
    : DialogFragment()
{
    private lateinit var binding: DialogImageBinding

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogImageBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(requireContext())
        builder.setView(binding.root)
        val dialog = builder.create()

        if (title == "Schedule" && !isAdmin) binding.editImage.visibility = View.VISIBLE

        Picasso.get().load(url).into(binding.image)
        binding.title.text = title

        binding.editImage.setOnDebouncedClickListener {
            onEditClick!!.invoke()
            dialog.dismiss()
        }

        if (dialog.window != null) dialog.window!!.setBackgroundDrawable(0.toDrawable())
        return dialog
    }
}
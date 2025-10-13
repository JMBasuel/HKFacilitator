package com.upang.hkfacilitator.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.view.*
import android.widget.FrameLayout
import com.google.android.material.snackbar.Snackbar
import com.upang.hkfacilitator.databinding.SnackbarBinding

private var snackBar: Snackbar? = null

@SuppressLint("RestrictedApi")
fun snackBar(view: View?, message: String) {
    if (view != null && view.context is Activity && !(view.context as Activity).isFinishing &&
        !(view.context as Activity).isDestroyed) {

        snackBar = Snackbar.make(view, "", Snackbar.LENGTH_LONG)
        snackBar!!.view.setBackgroundColor(Color.TRANSPARENT)

        val params = snackBar!!.view.layoutParams as FrameLayout.LayoutParams
        params.gravity = Gravity.BOTTOM or Gravity.CENTER
        params.setMargins(50, 0, 50, 200)
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        snackBar!!.view.layoutParams = params

        val snackBarLayout = snackBar!!.view as? Snackbar.SnackbarLayout
        snackBarLayout!!.setPadding(0, 0, 0, 0)

        val snackBarBinding = SnackbarBinding.inflate(LayoutInflater.from(view.context))
        snackBarBinding.toastMessage.text = message
        snackBarLayout.addView(snackBarBinding.root)

        return snackBar!!.show()
    }
}

fun cancelSnackBar() {
    snackBar?.dismiss()
}
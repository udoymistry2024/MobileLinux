package com.mobilelinux.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.mobilelinux.R

/**
 * Modern custom dialog for renaming terminal sessions.
 */
object RenameSessionDialog {

    fun show(context: Context, currentName: String, onResult: (String) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_file_action, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_file_name)
        val til = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.til_file_name)
        til?.hint = "Session name"
        etName.setText(currentName)
        etName.setSelection(etName.text.length)

        CustomDialog.Builder(context)
            .setIcon(R.drawable.ic_edit, ContextCompat.getColor(context, R.color.accent_yellow))
            .setTitle("Rename Session")
            .setMessage("Enter a new name for this session:")
            .setView(dialogView)
            .setPositiveButtonWithResult("Rename") {
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    onResult(name)
                    true
                } else {
                    false
                }
            }
            .setNeutralButton("Cancel")
            .show()
    }

    fun show(fm: FragmentManager, currentName: String, onResult: (String) -> Unit) {
        val ctx = fm.fragments.firstOrNull()?.context
        if (ctx != null) {
            show(ctx, currentName, onResult)
        }
    }
}

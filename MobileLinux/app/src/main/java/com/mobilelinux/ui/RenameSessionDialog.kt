package com.mobilelinux.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.mobilelinux.R

class RenameSessionDialog : DialogFragment() {

    private var onResult: ((String) -> Unit)? = null
    private var currentName: String = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val editText = EditText(requireContext()).apply {
            setText(currentName)
            selectAll()
            setPadding(48, 24, 48, 24)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Rename Session")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) onResult?.invoke(name)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    companion object {
        fun show(fm: FragmentManager, currentName: String, onResult: (String) -> Unit) {
            RenameSessionDialog().apply {
                this.currentName = currentName
                this.onResult = onResult
            }.show(fm, "rename_dialog")
        }
    }
}

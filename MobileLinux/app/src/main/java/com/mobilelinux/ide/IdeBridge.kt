package com.mobilelinux.ide

import android.webkit.JavascriptInterface

/**
 * JavaScript bridge between WebView (Ace Editor) and Kotlin.
 */
class IdeBridge(
    private val onReady: () -> Unit,
    private val onDirtyChanged: (Boolean) -> Unit,
    private val onCursor: (row: Int, col: Int) -> Unit,
    private val onSave: () -> Unit
) {
    @JavascriptInterface
    fun onEditorReady() {
        onReady()
    }

    @JavascriptInterface
    fun onContentChanged(isDirty: Boolean) {
        onDirtyChanged(isDirty)
    }

    @JavascriptInterface
    fun onCursorChanged(line: Int, col: Int) {
        onCursor(line, col)
    }

    @JavascriptInterface
    fun onSaveShortcut() {
        onSave()
    }
}

package com.mobilelinux.ide

import android.webkit.JavascriptInterface

/**
 * JavaScript bridge between WebView (Ace Editor) and Kotlin.
 */
class IdeBridge(
    private val onReady: () -> Unit,
    private val onDirtyChanged: (Boolean) -> Unit,
    private val onCursor: (row: Int, col: Int) -> Unit,
    private val onSave: () -> Unit,
    private val onCopyText: (String) -> Unit = {},
    private val onCutText: (String) -> Unit = {},
    private val onPasteReq: () -> Unit = {},
    private val onCtrlReset: () -> Unit = {},
    private val onCustomEditorChangedCallback: (Boolean, String) -> Unit = { _, _ -> },
    private val onUndoRedoState: (canUndo: Boolean, canRedo: Boolean) -> Unit = { _, _ -> }
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

    @JavascriptInterface
    fun onCopy(text: String) {
        onCopyText(text)
    }

    @JavascriptInterface
    fun onCut(text: String) {
        onCutText(text)
    }

    @JavascriptInterface
    fun onPasteRequest() {
        onPasteReq()
    }

    @JavascriptInterface
    fun onCtrlModifierReset() {
        onCtrlReset()
    }

    @JavascriptInterface
    fun onCustomEditorChanged(isActive: Boolean, editorName: String) {
        onCustomEditorChangedCallback(isActive, editorName)
    }

    @JavascriptInterface
    fun onUndoRedoStateChanged(canUndo: Boolean, canRedo: Boolean) {
        onUndoRedoState(canUndo, canRedo)
    }
}

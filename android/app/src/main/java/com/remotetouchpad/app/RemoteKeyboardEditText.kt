package com.remotetouchpad.app

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

/**
 * Normal text changes are reported via TextWatcher, but when this field is
 * already empty, pressing delete produces no text change to listen for
 * (nothing local to delete) — yet the user still wants that backspace sent
 * to the laptop. Soft keyboards mostly call deleteSurroundingText() instead
 * of sending a raw key event, so both paths are intercepted here.
 */
class RemoteKeyboardEditText(context: Context, attrs: AttributeSet?) : AppCompatEditText(context, attrs) {

    var onEmptyBackspace: (() -> Unit)? = null

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(target, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (text.isNullOrEmpty() && beforeLength > 0) {
                    onEmptyBackspace?.invoke()
                    return true
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_DEL &&
                    event.action == KeyEvent.ACTION_DOWN &&
                    text.isNullOrEmpty()
                ) {
                    onEmptyBackspace?.invoke()
                    return true
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && text.isNullOrEmpty()) {
            onEmptyBackspace?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

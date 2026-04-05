package com.flowvoice.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.flowvoice.android.overlay.OverlayService

/**
 * Accessibility service that:
 * - Listens for focus events on editable text fields across all apps
 * - Stores a reference to the focused node for later text injection
 * - Triggers the floating overlay to appear when an editable field is focused
 * - Injects transcribed text into the focused field via ACTION_SET_TEXT
 */
class FlowVoiceAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: FlowVoiceAccessibilityService? = null
            private set

        // Holds the currently focused editable node.
        // We own this reference and must recycle it when done.
        @Volatile private var focusedNode: AccessibilityNodeInfo? = null
    }

    override fun onServiceConnected() {
        instance = this
        // Auto-start the overlay service when the accessibility service is enabled,
        // but only if the overlay permission has already been granted.
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val source = event.source
                // Null source means focus moved to a non-focusable area - hide the overlay
                if (source == null || !source.isEditable) {
                    source?.recycle()
                    clearFocusedNode()
                    OverlayService.instance?.hide()
                    return
                }
                focusedNode?.recycle()
                focusedNode = source
                OverlayService.instance?.show()
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // User tapped a non-editable element: hide unless an editable field
                // will immediately gain focus (TYPE_VIEW_FOCUSED will follow in that case)
                val source = event.source
                if (source != null && !source.isEditable) {
                    source.recycle()
                    clearFocusedNode()
                    OverlayService.instance?.hide()
                } else {
                    source?.recycle()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Hide only if the new window is NOT the soft keyboard (IME).
                // When the user taps a text field, the keyboard opens and fires this
                // event - we must not hide in that case.
                val imeVisible = windows?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
                if (!imeVisible) {
                    clearFocusedNode()
                    OverlayService.instance?.hide()
                }
            }
        }
    }

    override fun onInterrupt() {
        clearFocusedNode()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        clearFocusedNode()
        return super.onUnbind(intent)
    }

    /**
     * Appends [text] to whatever is already in the focused text field.
     * A space is inserted between the existing content and the new text
     * unless the field is empty or already ends with a whitespace character.
     *
     * Returns true if injection succeeded, false if fallback (clipboard) is needed.
     * Must be called from the main thread.
     */
    fun injectText(text: String): Boolean {
        val node = focusedNode ?: return false

        val rawText = node.text?.toString() ?: ""
        val hintText = node.hintText?.toString() ?: ""
        // Ignore the field content if it only contains the hint/placeholder text
        val existing = if (rawText.isEmpty() || rawText == hintText) "" else rawText
        val combined = when {
            existing.isEmpty() -> text
            existing.last().isWhitespace() -> existing + text
            else -> "$existing $text"
        }

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            combined
        )
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!success) {
            // Fallback: try clipboard paste (appends at cursor position)
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        return success
    }

    private fun clearFocusedNode() {
        focusedNode?.recycle()
        focusedNode = null
    }
}

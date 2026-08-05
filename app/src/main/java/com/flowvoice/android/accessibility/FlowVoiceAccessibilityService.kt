package com.flowvoice.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.flowvoice.android.overlay.OverlayService

/**
 * Accessibility service that:
 * - Listens for focus events on editable text fields across all apps
 * - Stores a reference to the focused node for later text injection
 * - Injects transcribed text into the focused field via ACTION_SET_TEXT
 *
 * The floating overlay is not driven by focus events: it stays visible for the
 * whole lifetime of [OverlayService].
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
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return

        val source = event.source ?: return
        // Only editable nodes are worth keeping. Focus moving to a non-editable
        // element does not clear the stored node: the user may still dictate, and
        // injectText() validates the node before writing into it.
        if (!source.isEditable) {
            source.recycle()
            return
        }
        focusedNode?.recycle()
        focusedNode = source
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

        // Refresh the node to get the current text, not the stale snapshot
        // captured at focus time. Without this, repeated dictations overwrite
        // each other because node.text still reflects the state before the
        // previous ACTION_SET_TEXT was applied.
        // A failed refresh means the field no longer exists (window closed,
        // list item recycled): drop it so the caller uses the clipboard fallback.
        if (!node.refresh()) {
            clearFocusedNode()
            return false
        }

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

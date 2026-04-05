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
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return

        val source = event.source ?: return
        if (!source.isEditable) {
            source.recycle()
            // Non-editable focus: hide the overlay
            OverlayService.instance?.hide()
            return
        }

        // Replace previous focused node reference
        focusedNode?.recycle()
        focusedNode = source

        OverlayService.instance?.show()
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
     * Injects [text] into the currently focused text field.
     *
     * Returns true if injection succeeded, false if fallback (clipboard) is needed.
     * Must be called from the main thread.
     */
    fun injectText(text: String): Boolean {
        val node = focusedNode ?: return false

        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!success) {
            // Fallback: try clipboard paste
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        return success
    }

    private fun clearFocusedNode() {
        focusedNode?.recycle()
        focusedNode = null
    }
}

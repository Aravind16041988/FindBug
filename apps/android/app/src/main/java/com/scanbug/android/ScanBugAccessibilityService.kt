package com.scanbug.android

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Strictly read-only UI explorer. It lists exposed controls but never clicks,
 * types, scrolls, or otherwise performs actions in the selected target app.
 */
class ScanBugAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var statusText: TextView? = null
    private var inventoryText: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private fun showFloatingPanel() {
        if (panel != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xEE202B3C.toInt())
        }
        container.addView(TextView(this).apply {
            text = "SCANBUG • READ-ONLY EXPLORER"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Explore screen"
            isAllCaps = false
            setOnClickListener { exploreCurrentScreen() }
        })
        actions.addView(Button(this).apply {
            text = "Stop"
            isAllCaps = false
            setOnClickListener {
                prefs.edit().remove(KEY_TARGET_PACKAGE).apply()
                removeFloatingPanel()
            }
        })
        container.addView(actions)

        inventoryText = TextView(this).apply {
            text = "Explore lists visible controls only. ScanBug does not tap controls or enter text."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            setPadding(0, 6, 0, 6)
        }
        container.addView(ScrollView(this).apply {
            addView(inventoryText)
            layoutParams = LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, 260
            )
        })
        statusText = TextView(this).apply {
            text = "Read-only inspection. Perform actions manually in the target app."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
        }
        container.addView(statusText)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 100
        }
        runCatching {
            windowManager?.addView(container, params)
            panel = container
        }.onFailure {
            Toast.makeText(this, "Could not display ScanBug overlay: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun removeFloatingPanel() {
        panel?.let { view -> runCatching { windowManager?.removeView(view) } }
        panel = null
        statusText = null
        inventoryText = null
    }

    private fun targetRoot(): AccessibilityNodeInfo? {
        val targetPackage = prefs.getString(KEY_TARGET_PACKAGE, null) ?: return null
        for (window in windows) {
            val candidate = window.root ?: continue
            if (candidate.packageName?.toString() == targetPackage) return candidate
            candidate.recycle()
        }
        val active = rootInActiveWindow ?: return null
        if (active.packageName?.toString() == targetPackage) return active
        active.recycle()
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Overlay/UI events from ScanBug itself must not affect the target session.
        if (event.packageName?.toString() == packageName) return

        val selectedPackage = prefs.getString(KEY_TARGET_PACKAGE, null)
        if (selectedPackage.isNullOrBlank()) {
            removeFloatingPanel()
            return
        }

        val eventPackage = event.packageName?.toString()

        // Keep the overlay stable during content changes from other windows
        // (for example system UI/keyboard). Only a real app-window transition
        // away from the selected target should close the panel.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (eventPackage == selectedPackage) {
                showFloatingPanel()
            } else if (!eventPackage.isNullOrBlank()) {
                removeFloatingPanel()
            }
            return
        }

        // Content-change events from the selected app keep the panel visible.
        if (eventPackage == selectedPackage) {
            showFloatingPanel()
        }
    }

    private fun exploreCurrentScreen() {
        val root = targetRoot()
        if (root == null) {
            statusText?.text = "Target screen is not available. Reopen the selected app."
            return
        }
        val controls = mutableListOf<String>()
        collectControls(root, controls, 0)
        root.recycle()
        inventoryText?.text = if (controls.isEmpty()) {
            "No labelled interactive controls were exposed. Custom-drawn or unlabeled controls may not be discoverable."
        } else {
            "READ-ONLY INVENTORY — ${controls.size} controls\n\n" +
                controls.take(MAX_CONTROLS).joinToString("\n") +
                if (controls.size > MAX_CONTROLS) "\n\nShowing first $MAX_CONTROLS controls." else ""
        }
        statusText?.text = "Scan complete. No target-app actions were performed."
    }

    private fun collectControls(node: AccessibilityNodeInfo, output: MutableList<String>, depth: Int) {
        if (depth > 16 || output.size >= 100) return
        val label = nodeLabel(node)
        if (node.isVisibleToUser && node.isEnabled && (node.isClickable || node.isEditable)) {
            val type = if (node.isEditable) "INPUT" else "CONTROL"
            output.add("${output.size + 1}. [$type] ${label.ifBlank { "(unlabelled control)" }}")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectControls(child, output, depth + 1)
            child.recycle()
        }
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        // Never read or persist editable field contents or password values.
        if (node.isPassword) return if (description.isNotBlank()) description else "Password field"
        if (node.isEditable) {
            val hint = node.hintText?.toString()?.trim().orEmpty()
            return listOf(description, hint).filter { it.isNotBlank() }.distinct().joinToString(" / ")
        }
        val text = node.text?.toString()?.trim().orEmpty()
        return listOf(text, description).filter { it.isNotBlank() }.distinct().joinToString(" / ")
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeFloatingPanel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "scanbug_local"
        const val KEY_TARGET_PACKAGE = "selected_target_package"
        private const val MAX_CONTROLS = 40
    }
}

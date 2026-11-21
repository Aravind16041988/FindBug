package com.scanbug.android

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * User-visible, deterministic UI inspection for the currently selected app.
 * It never taps controls automatically: every interaction requires an explicit
 * label entry and tap from the ScanBug overlay.
 */
class ScanBugAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var labelInput: EditText? = null
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
            text = "SCANBUG QA • ON DEVICE"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        })
        labelInput = EditText(this).apply {
            hint = "Accessible control label"
            textSize = 14f
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFCCCCCC.toInt())
        }
        container.addView(labelInput, LinearLayout.LayoutParams(280, WindowManager.LayoutParams.WRAP_CONTENT))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Explore screen"
            isAllCaps = false
            setOnClickListener { exploreCurrentScreen() }
        })
        actions.addView(Button(this).apply {
            text = "Tap label"
            isAllCaps = false
            setOnClickListener {
                val label = labelInput?.text?.toString()?.trim().orEmpty()
                if (label.isBlank()) {
                    labelInput?.error = "Enter a visible label"
                } else if (isPotentiallyDestructive(label)) {
                    statusText?.text = "Blocked for safety: '$label' may perform a sensitive action."
                } else {
                    val ok = tapVisibleLabel(label)
                    statusText?.text = if (ok) "Tapped: $label" else "No exact, enabled clickable match found."
                }
            }
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
            text = "Tap Explore screen to list accessible controls. No controls are tapped automatically."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            setPadding(0, 6, 0, 6)
        }
        val inventoryScroll = ScrollView(this).apply {
            addView(inventoryText)
            layoutParams = LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                260
            )
        }
        container.addView(inventoryScroll)
        statusText = TextView(this).apply {
            text = "Ready — exploration is read-only until you explicitly tap a label."
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
        labelInput = null
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
        if (event.packageName?.toString() == packageName) return
        val selectedPackage = prefs.getString(KEY_TARGET_PACKAGE, null)
        if (selectedPackage.isNullOrBlank() || event.packageName?.toString() != selectedPackage) {
            removeFloatingPanel()
            return
        }
        showFloatingPanel()
        val root = targetRoot() ?: return
        val lines = mutableListOf<String>()
        collectSnapshot(root, lines, 0)
        prefs.edit().putString(KEY_SNAPSHOT, lines.distinct().take(150).joinToString("\n").ifBlank {
            "No accessible text or controls found on this screen."
        }).apply()
        root.recycle()
    }

    private fun collectSnapshot(node: AccessibilityNodeInfo, output: MutableList<String>, depth: Int) {
        if (depth > 14 || output.size >= 200) return
        val label = nodeLabel(node)
        if (label.isNotBlank()) {
            val flags = buildList {
                if (node.isClickable) add("clickable")
                if (node.isEditable) add("editable")
                if (node.isEnabled) add("enabled")
                if (node.isScrollable) add("scrollable")
            }.joinToString(", ")
            output.add("${"  ".repeat(depth)}• $label" + if (flags.isNotBlank()) " [$flags]" else "")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSnapshot(child, output, depth + 1)
            child.recycle()
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
            "No labelled interactive controls were exposed by this screen. Custom-drawn or unlabeled controls may not be discoverable."
        } else {
            "SCREEN INVENTORY — ${controls.size} labelled controls\n\n" +
                controls.take(MAX_CONTROLS).joinToString("\n") +
                if (controls.size > MAX_CONTROLS) "\n\nShowing first $MAX_CONTROLS controls." else ""
        }
        statusText?.text = "Read-only scan complete. Review a label, then tap it manually if safe."
    }

    private fun collectControls(node: AccessibilityNodeInfo, output: MutableList<String>, depth: Int) {
        if (depth > 16 || output.size >= 100) return
        val label = nodeLabel(node)
        if (node.isVisibleToUser && node.isEnabled && (node.isClickable || node.isEditable)) {
            val type = if (node.isEditable) "INPUT" else "BUTTON"
            val safeNote = if (isPotentiallyDestructive(label)) " [SENSITIVE — NOT TO BE TAPPED]" else ""
            output.add("${output.size + 1}. [$type] ${label.ifBlank { "(unlabelled control)" }}$safeNote")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectControls(child, output, depth + 1)
            child.recycle()
        }
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        return listOf(text, description).filter { it.isNotBlank() }.distinct().joinToString(" / ")
    }

    private fun isPotentiallyDestructive(label: String): Boolean {
        val normalized = label.lowercase()
        return BLOCKED_TERMS.any { term -> normalized.contains(term) }
    }

    private fun tapVisibleLabel(requested: String): Boolean {
        val root = targetRoot() ?: return false
        val matches = root.findAccessibilityNodeInfosByText(requested)
        val candidate = matches.firstOrNull {
            it.isVisibleToUser && it.isEnabled &&
                (it.text?.toString()?.equals(requested, true) == true ||
                 it.contentDescription?.toString()?.equals(requested, true) == true)
        }
        var actionNode = candidate
        var depth = 0
        while (actionNode != null && !actionNode.isClickable && depth < 5) {
            actionNode = actionNode.parent
            depth++
        }
        val success = actionNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (actionNode !== candidate) actionNode?.recycle()
        matches.forEach { runCatching { it.recycle() } }
        root.recycle()
        return success
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeFloatingPanel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "scanbug_local"
        const val KEY_SNAPSHOT = "latest_accessible_snapshot"
        const val KEY_TARGET_PACKAGE = "selected_target_package"
        private const val MAX_CONTROLS = 40
        private val BLOCKED_TERMS = listOf(
            "pay", "payment", "transfer", "delete", "remove", "purchase", "buy",
            "logout", "log out", "sign out", "confirm", "submit", "send",
            "withdraw", "close account", "place order"
        )
    }
}

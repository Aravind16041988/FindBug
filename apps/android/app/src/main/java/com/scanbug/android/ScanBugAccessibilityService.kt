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
import android.widget.TextView
import android.widget.Toast

class ScanBugAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var labelInput: EditText? = null
    private var statusText: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingPanel()
    }

    private fun showFloatingPanel() {
        if (panel != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xEE202B3C.toInt())
        }
        container.addView(TextView(this).apply {
            text = "SCANBUG QA  •  ON DEVICE"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        })
        labelInput = EditText(this).apply {
            hint = "Accessible button label"
            textSize = 14f
            setSingleLine(true)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFFCCCCCC.toInt())
        }
        container.addView(labelInput, LinearLayout.LayoutParams(260, WindowManager.LayoutParams.WRAP_CONTENT))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "Tap label"
            isAllCaps = false
            setOnClickListener {
                val label = labelInput?.text?.toString()?.trim().orEmpty()
                if (label.isBlank()) {
                    labelInput?.error = "Enter visible label"
                } else {
                    val ok = tapVisibleLabel(label)
                    statusText?.text = if (ok) "Tapped: $label" else "No matching clickable control"
                }
            }
        })
        actions.addView(Button(this).apply {
            text = "Hide"
            isAllCaps = false
            setOnClickListener { removeFloatingPanel() }
        })
        container.addView(actions)
        statusText = TextView(this).apply {
            text = "Choose a visible label to tap"
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val root = rootInActiveWindow ?: return
        val lines = mutableListOf<String>()
        collectVisibleNodes(root, lines, 0)
        prefs.edit().putString(KEY_SNAPSHOT, lines.distinct().take(100).joinToString("\n").ifBlank {
            "No accessible text or controls found on this screen."
        }).apply()
        root.recycle()
    }

    private fun collectVisibleNodes(node: AccessibilityNodeInfo, output: MutableList<String>, depth: Int) {
        if (depth > 12 || output.size >= 150) return
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val label = listOf(text, description).filter { it.isNotBlank() }.distinct().joinToString(" / ")
        if (label.isNotBlank()) {
            val flags = buildList {
                if (node.isClickable) add("clickable")
                if (node.isEditable) add("editable")
                if (node.isEnabled) add("enabled")
            }.joinToString(", ")
            output.add("${"  ".repeat(depth)}• $label" + if (flags.isNotBlank()) " [$flags]" else "")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectVisibleNodes(child, output, depth + 1)
            child.recycle()
        }
    }

    private fun tapVisibleLabel(requested: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(requested)
        val exact = matches.firstOrNull {
            it.isVisibleToUser && it.isEnabled &&
                (it.text?.toString()?.equals(requested, true) == true ||
                 it.contentDescription?.toString()?.equals(requested, true) == true)
        }
        val candidate = exact ?: matches.firstOrNull { it.isVisibleToUser && it.isEnabled }
        var clickable: AccessibilityNodeInfo? = candidate
        var depth = 0
        while (clickable != null && !clickable.isClickable && depth < 5) {
            clickable = clickable.parent
            depth++
        }
        val success = clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        clickable?.recycle()
        matches.forEach { runCatching { it.recycle() } }
        root.recycle()
        return success
    }

    override fun onInterrupt() { }

    override fun onDestroy() {
        removeFloatingPanel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "scanbug_local"
        const val KEY_SNAPSHOT = "latest_accessible_snapshot"
    }
}

package com.scanbug.android

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Read-only UI inventory. It never invokes target-app actions or reads editable values.
 * Reports are shared only after the user explicitly taps Share inventory.
 */
class ScanBugAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var statusText: TextView? = null
    private var inventoryText: TextView? = null
    private var latestReport: String = ""
    private var isRecording = false
    private val recordedSteps = mutableListOf<RecordedStep>()
    private data class RecordedStep(
        val action: String,
        val controlType: String,
        val resourceId: String,
        val bounds: String,
        var expected: String = "",
        var actual: String = "",
        var result: String = "NOT RUN"
    )
    private var skippedSensitiveSteps = 0

    private data class ControlInfo(
        val category: String,
        val type: String,
        val label: String,
        val resourceId: String,
        val className: String,
        val bounds: String,
        val hierarchy: String,
        val enabled: Boolean,
        val clickable: Boolean,
        val checkable: Boolean,
        val checked: Boolean,
        val selected: Boolean
    )

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
            text = "Share inventory"
            isAllCaps = false
            setOnClickListener { shareInventory() }
        })
        actions.addView(Button(this).apply {
            text = "Stop"
            isAllCaps = false
            setOnClickListener {
                prefs.edit().remove(KEY_TARGET_PACKAGE).apply()
                isRecording = false
                recordedSteps.clear()
                latestReport = ""
                removeFloatingPanel()
            }
        })
        container.addView(actions)

        val recorderActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        recorderActions.addView(Button(this).apply {
            text = "Start recording"
            isAllCaps = false
            setOnClickListener {
                recordedSteps.clear()
                skippedSensitiveSteps = 0
                isRecording = true
                statusText?.text = "Recording manual taps. ScanBug will not perform actions."
            }
        })
        recorderActions.addView(Button(this).apply {
            text = "Stop recording"
            isAllCaps = false
            setOnClickListener { stopRecording() }
        })
        recorderActions.addView(Button(this).apply {
            text = "Share test flow"
            isAllCaps = false
            setOnClickListener { shareTestFlow() }
        })
        container.addView(recorderActions)

        inventoryText = TextView(this).apply {
            text = "Explore lists accessible controls and metadata only. It never taps controls or enters text."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            setPadding(0, 6, 0, 6)
        }
        container.addView(ScrollView(this).apply {
            addView(inventoryText)
            layoutParams = LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, 340
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
            y = 80
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
        if (event.packageName?.toString() == packageName) return

        val selectedPackage = prefs.getString(KEY_TARGET_PACKAGE, null)
        if (selectedPackage.isNullOrBlank()) {
            removeFloatingPanel()
            return
        }
        val eventPackage = event.packageName?.toString()

        if (isRecording && eventPackage == selectedPackage &&
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            recordManualTap(event, selectedPackage)
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (eventPackage == selectedPackage) {
                showFloatingPanel()
            } else if (!eventPackage.isNullOrBlank()) {
                removeFloatingPanel()
            }
            return
        }
        if (eventPackage == selectedPackage) showFloatingPanel()
    }

    private fun recordManualTap(event: AccessibilityEvent, selectedPackage: String) {
        val source = event.source ?: return
        try {
            if (source.packageName?.toString() != selectedPackage) return
            var ancestor: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(source)
            var depth = 0
            var blocked = false
            while (ancestor != null && depth < 6) {
                if (ScanBugSafetyPolicy.isPotentiallyDestructive(nodeLabel(ancestor))) {
                    blocked = true
                    break
                }
                val parent = ancestor.parent
                ancestor.recycle()
                ancestor = parent
                depth++
            }
            ancestor?.recycle()
            if (blocked) {
                skippedSensitiveSteps++
                return
            }
            val label = nodeLabel(source).ifBlank {
                friendlyType(source, source.className?.toString().orEmpty())
            }.replace(Regex("\\s+"), " ").take(100)
            val className = source.className?.toString()?.substringAfterLast('.').orEmpty()
            val resourceId = source.viewIdResourceName.orEmpty()
            val bounds = Rect()
            source.getBoundsInScreen(bounds)
            val number = recordedSteps.size + 1
            recordedSteps.add(
                number.toString() + ". Tap: " + label +
                    " | Type: " + className.ifBlank { "Unknown" } +
                    " | Resource ID: " + resourceId.ifBlank { "not exposed" } +
                    " | Bounds: " + bounds.flattenToString()
            )
            statusText?.text = "Recording manual taps: " + recordedSteps.size + " steps captured."
        } finally {
            source.recycle()
        }
    }

    private fun stopRecording() {
        isRecording = false
        inventoryText?.text = if (recordedSteps.isEmpty()) {
            "No manual taps recorded. Start recording, then perform actions yourself in the target app."
        } else {
            "RECORDED TEST FLOW — " + recordedSteps.size + " steps\\n\\n" +
                recordedSteps.joinToString("\\n") +
                if (skippedSensitiveSteps > 0) "\\n\\n" + skippedSensitiveSteps +
                    " sensitive/destructive action(s) omitted." else ""
        }
        statusText?.text = "Recording stopped. ScanBug did not execute any actions."
    }

    private fun shareTestFlow() {
        if (isRecording) {
            statusText?.text = "Stop recording before sharing the test flow."
            return
        }
        if (recordedSteps.isEmpty()) {
            statusText?.text = "No recorded steps to share."
            return
        }
        val targetPackage = prefs.getString(KEY_TARGET_PACKAGE, null).orEmpty()
        val report = "ScanBug Manual Test Flow\\n" +
            "Target package: " + targetPackage + "\\n" +
            "Steps: " + recordedSteps.size + "\\n" +
            "Sensitive/destructive actions omitted: " + skippedSensitiveSteps + "\\n" +
            "Privacy: editable field values and password values are not recorded.\\n" +
            "Execution: manual user actions only; ScanBug did not replay or perform actions.\\n\\n" +
            recordedSteps.joinToString("\\n")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ScanBug Manual Test Flow")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching {
            startActivity(Intent.createChooser(send, "Share ScanBug test flow").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { statusText?.text = "Could not open share sheet: " + it.message }
    }

    private fun exploreCurrentScreen() {
        val targetPackage = prefs.getString(KEY_TARGET_PACKAGE, null).orEmpty()
        val root = targetRoot()
        if (root == null) {
            statusText?.text = "Target screen is not available. Reopen the selected app."
            return
        }

        val controls = mutableListOf<ControlInfo>()
        collectControls(root, controls, 0, "root")
        root.recycle()

        latestReport = buildReport(targetPackage, controls)
        inventoryText?.text = if (controls.isEmpty()) {
            "No accessible controls found. Custom-drawn, protected or unlabeled controls may not be exposed."
        } else {
            renderInventory(controls)
        }
        statusText?.text = "Scan complete: ${controls.size} controls. Read-only; nothing was tapped or changed."
    }

    private fun collectControls(
        node: AccessibilityNodeInfo,
        output: MutableList<ControlInfo>,
        depth: Int,
        path: String
    ) {
        if (depth > MAX_DEPTH || output.size >= MAX_CONTROLS) return

        val interactive = node.isClickable || node.isEditable || node.isCheckable ||
            node.isLongClickable || node.isScrollable
        if (node.isVisibleToUser && interactive) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val className = node.className?.toString().orEmpty()
            val category = categoryFor(node, className)
            output.add(
                ControlInfo(
                    category = category,
                    type = friendlyType(node, className),
                    label = nodeLabel(node).ifBlank { friendlyType(node, className) },
                    resourceId = node.viewIdResourceName.orEmpty(),
                    className = className.substringAfterLast('.'),
                    bounds = "${bounds.left},${bounds.top} - ${bounds.right},${bounds.bottom}",
                    hierarchy = path,
                    enabled = node.isEnabled,
                    clickable = node.isClickable,
                    checkable = node.isCheckable,
                    checked = node.isChecked,
                    selected = node.isSelected
                )
            )
        }

        for (i in 0 until node.childCount) {
            if (output.size >= MAX_CONTROLS) break
            val child = node.getChild(i) ?: continue
            collectControls(child, output, depth + 1, "$path/$i")
            child.recycle()
        }
    }

    private fun categoryFor(node: AccessibilityNodeInfo, className: String): String {
        val lower = className.lowercase()
        return when {
            node.isEditable -> "INPUTS"
            lower.contains("tab") -> "TABS"
            node.isCheckable || lower.contains("switch") || lower.contains("checkbox") || lower.contains("radio") -> "SELECTIONS"
            node.isClickable || node.isLongClickable -> "BUTTONS & ACTIONS"
            else -> "OTHER CONTROLS"
        }
    }

    private fun friendlyType(node: AccessibilityNodeInfo, className: String): String {
        val lower = className.lowercase()
        return when {
            node.isPassword -> "Password field (value hidden)"
            node.isEditable -> "Text input"
            lower.contains("image") -> "Image"
            lower.contains("tab") -> "Tab"
            lower.contains("switch") -> "Switch"
            lower.contains("checkbox") -> "Checkbox"
            lower.contains("radio") -> "Radio button"
            lower.contains("button") -> "Button"
            lower.contains("textview") -> "Text"
            lower.contains("edittext") -> "Text input"
            else -> className.substringAfterLast('.').ifBlank { "Control" }
        }
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        // Never read or expose editable field contents or password values.
        if (node.isPassword) return if (description.isNotBlank()) description else "Password field"
        if (node.isEditable) {
            val hint = node.hintText?.toString()?.trim().orEmpty()
            return listOf(description, hint).filter { it.isNotBlank() }.distinct().joinToString(" / ")
        }
        val text = node.text?.toString()?.trim().orEmpty()
        return listOf(text, description).filter { it.isNotBlank() }.distinct().joinToString(" / ")
    }

    private fun renderInventory(controls: List<ControlInfo>): String {
        val result = StringBuilder("READ-ONLY INVENTORY — ${controls.size} controls\n")
        val groups = listOf("INPUTS", "BUTTONS & ACTIONS", "TABS", "SELECTIONS", "OTHER CONTROLS")
        for (group in groups) {
            val entries = controls.filter { it.category == group }
            if (entries.isEmpty()) continue
            result.append("\n").append(group).append(" (").append(entries.size).append(")\n")
            entries.forEachIndexed { index, item ->
                result.append("\n").append(index + 1).append(". ").append(item.label)
                result.append("\n   Type: ").append(item.type)
                result.append("\n   Resource ID: ").append(item.resourceId.ifBlank { "not exposed" })
                result.append("\n   Class: ").append(item.className.ifBlank { "not exposed" })
                result.append("\n   Bounds: ").append(item.bounds)
                result.append("\n   Hierarchy: ").append(item.hierarchy)
                result.append("\n   State: ").append(
                    listOf(
                        if (item.enabled) "enabled" else "disabled",
                        if (item.clickable) "clickable" else null,
                        if (item.checkable) if (item.checked) "checked" else "unchecked" else null,
                        if (item.selected) "selected" else null
                    ).filterNotNull().joinToString(", ")
                ).append("\n")
            }
        }
        if (controls.size >= MAX_CONTROLS) {
            result.append("\nSafety limit: first $MAX_CONTROLS controls shown.")
        }
        return result.toString()
    }

    private fun buildReport(targetPackage: String, controls: List<ControlInfo>): String {
        return "ScanBug UI Inventory\nTarget package: $targetPackage\nControls: ${controls.size}\n" +
            "Privacy: editable values/password values excluded; report generated locally on user request.\n\n" +
            renderInventory(controls)
    }

    private fun shareInventory() {
        if (latestReport.isBlank()) {
            statusText?.text = "Explore the screen first. No inventory is ready to share."
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ScanBug UI Inventory")
            putExtra(Intent.EXTRA_TEXT, latestReport)
        }
        val chooser = Intent.createChooser(send, "Share ScanBug inventory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(chooser) }
            .onFailure { statusText?.text = "Could not open share sheet: ${it.message}" }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        removeFloatingPanel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "scanbug_local"
        const val KEY_TARGET_PACKAGE = "selected_target_package"
        private const val MAX_CONTROLS = 100
        private const val MAX_DEPTH = 20
    }
}

package com.scanbug.android

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
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
 * Controlled QA companion: inventory is read-only; replay clicks require explicit per-step approval. Editable values are never read.
 * Reports are shared only after the user explicitly taps Share inventory.
 */
class ScanBugAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var windowManager: WindowManager? = null
    private var panel: LinearLayout? = null
    private var statusText: TextView? = null
    private var inventoryText: TextView? = null
    private var recordingText: TextView? = null
    private var highlightView: View? = null
    private var recordingChip: TextView? = null
    private var isRecordingPaused = false
    private var panelMinimizedForRecording = false
    private var isAiTesting = false
    private var aiActionCount = 0
    private val aiHandler = Handler(Looper.getMainLooper())
    private val aiVisited = mutableSetOf<String>()
    private val aiResults = mutableListOf<String>()
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
        var result: String = "NOT RUN",
        var replayed: Boolean = false
    )
    private var skippedSensitiveSteps = 0
    private var replayIndex = 0

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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showFloatingPanel() {
        if (panel != null) return
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(0xFFF7F4EF.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xFFE8E1D8.toInt())
            }
            elevation = dp(10).toFloat()
        }
        container.addView(TextView(this).apply {
            text = "ScanBug"
            setTextColor(0xFF25231F.toInt())
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })
        container.addView(TextView(this).apply {
            text = "CONTROLLED QA EXPLORER"
            setTextColor(0xFF77716A.toInt())
            textSize = 10f
            letterSpacing = 0.12f
        })
        recordingText = TextView(this).apply {
            text = "● READY  ·  Manual mode"
            setTextColor(0xFF68635C.toInt())
            textSize = 12f
            setPadding(0, dp(10), 0, dp(8))
        }
        container.addView(recordingText)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun action(label: String, onClick: () -> Unit, primary: Boolean = false) {
            content.addView(Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 14f
                setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF28251F.toInt())
                background = GradientDrawable().apply {
                    setColor(if (primary) 0xFFD97745.toInt() else 0xFFECE7DF.toInt())
                    cornerRadius = dp(12).toFloat()
                }
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { onClick() }
                layoutParams = LinearLayout.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, dp(46)
                ).apply { bottomMargin = dp(6) }
            })
        }
        action(if (isAiTesting) "Stop AI testing" else "Run AI testing", { if (isAiTesting) stopAiTesting("Stopped by tester.") else startAiTesting() }, true)
        action("Explore screen", { exploreCurrentScreen() })
        action("AI test suggestions", { showAiSuggestions() })
        action("Share inventory", { shareInventory() })
        action(if (isRecording) "Stop recording" else "Start recording", {
            if (isRecording) stopRecording() else startRecording()
        }, true)
        if (isRecording) action(if (isRecordingPaused) "Resume recording" else "Pause recording", { if (isRecordingPaused) resumeRecording() else pauseRecording() })
        action("Share test flow", { shareTestFlow() })
        action("Review assertions", { reviewAssertions() })
        action("Save / load test case", { showTestCaseActions() })
        action("Replay next · Reset replay", {
            previewNextReplayStep()
        })
        action("Save run / HTML report", { saveExecutionRun(); shareExecutionHistoryHtml() })
        action("Minimise panel", { removeFloatingPanel() })
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            addView(content)
            layoutParams = LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, dp(430)
            )
        }
        container.addView(scroll)
        inventoryText = TextView(this).apply {
            text = "Explore discovers accessible controls. AI suggestions are local heuristics until an approved AI provider is configured."
            setTextColor(0xFF514C45.toInt())
            textSize = 12f
            setPadding(0, dp(8), 0, dp(6))
        }
        container.addView(inventoryText)
        statusText = TextView(this).apply {
            text = "Ready. ScanBug will not perform actions without your approval."
            setTextColor(0xFF77716A.toInt())
            textSize = 11f
        }
        container.addView(statusText)

        val params = WindowManager.LayoutParams(
            dp(300), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(8)
            y = dp(70)
        }
        runCatching {
            windowManager?.addView(container, params)
            panel = container
            updateRecordingUi()
        }.onFailure {
            Toast.makeText(this, "Could not display ScanBug overlay: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showTestCaseActions() {
        AlertDialog.Builder(this)
            .setTitle("Test case")
            .setItems(arrayOf("Save test case", "Load saved test case")) { _, which ->
                if (which == 0) saveReusableTestCase() else loadSavedTestCase()
            }.show()
    }

    private fun startRecording() {
        recordedSteps.clear()
        replayIndex = 0
        skippedSensitiveSteps = 0
        isRecording = true
        isRecordingPaused = false
        updateRecordingUi()
        statusText?.text = "Recording started. ScanBug is minimised; use the target app normally."
        panelMinimizedForRecording = true
        removeFloatingPanel()
        showRecordingChip()
    }

    private fun pauseRecording() {
        if (!isRecording) return
        isRecordingPaused = true
        clearTapHighlight()
        updateRecordingUi()
    }

    private fun resumeRecording() {
        if (!isRecording) return
        isRecordingPaused = false
        updateRecordingUi()
    }

    private fun showRecordingChip() {
        if (recordingChip != null) return
        val chip = TextView(this).apply {
            text = if (isAiTesting) "✦ AI TEST  ·  0" else "● REC  ·  0"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                setColor(0xFFD45F3C.toInt())
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0xFFFFD6C8.toInt())
            }
            elevation = dp(8).toFloat()
            setOnClickListener {
                removeRecordingChip()
                panelMinimizedForRecording = false
                showFloatingPanel()
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, dp(46),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(90)
        }
        chip.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var dragged = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) dragged = true
                        if (dragged) {
                            params.x = (startX - dx).coerceAtLeast(0)
                            params.y = (startY + dy).coerceAtLeast(dp(24))
                            runCatching { windowManager?.updateViewLayout(chip, params) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) v.performClick()
                        return true
                    }
                }
                return false
            }
        })
        runCatching {
            windowManager?.addView(chip, params)
            recordingChip = chip
            updateRecordingChip()
        }.onFailure {
            Toast.makeText(this, "Could not minimise ScanBug: " + it.message, Toast.LENGTH_LONG).show()
            panelMinimizedForRecording = false
            showFloatingPanel()
        }
    }

    private fun updateRecordingChip() {
        recordingChip?.apply {
            text = when { isAiTesting -> "✦ AI TEST  ·  " + aiActionCount; isRecordingPaused -> "Ⅱ PAUSED  ·  " + recordedSteps.size; else -> "● REC  ·  " + recordedSteps.size }
            background = GradientDrawable().apply {
                setColor(if (isAiTesting) 0xFF315F8C.toInt() else if (isRecordingPaused) 0xFF6D6862.toInt() else 0xFFD45F3C.toInt())
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0xFFFFD6C8.toInt())
            }
        }
    }

    private fun removeRecordingChip() {
        recordingChip?.let { runCatching { windowManager?.removeView(it) } }
        recordingChip = null
    }

    private fun updateRecordingUi() {
        recordingText?.apply {
            text = if (isRecording) {
                if (isRecordingPaused) "Ⅱ PAUSED  ·  ${recordedSteps.size} actions"
                else "● RECORDING  ·  ${recordedSteps.size} actions"
            } else "○ READY  ·  ${recordedSteps.size} actions captured"
            setTextColor(if (isRecording) 0xFFB8422B.toInt() else 0xFF68635C.toInt())
        }
        updateRecordingChip()
    }

    private fun showTapHighlight(bounds: Rect) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        highlightView?.let { runCatching { windowManager?.removeView(it) } }
        val marker = View(this).apply {
            background = GradientDrawable().apply {
                setColor(0x22D97745)
                setStroke(dp(3), 0xFFD97745.toInt())
                cornerRadius = dp(8).toFloat()
            }
        }
        val params = WindowManager.LayoutParams(
            bounds.width().coerceAtLeast(dp(24)), bounds.height().coerceAtLeast(dp(24)),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.left
            y = bounds.top
        }
        runCatching {
            windowManager?.addView(marker, params)
            highlightView = marker
            Handler(Looper.getMainLooper()).postDelayed({
                if (highlightView === marker) clearTapHighlight()
            }, 900)
        }
    }

    private fun clearTapHighlight() {
        highlightView?.let { runCatching { windowManager?.removeView(it) } }
        highlightView = null
    }

    private fun removeFloatingPanel() {
        panel?.let { view -> runCatching { windowManager?.removeView(view) } }
        panel = null
        statusText = null
        inventoryText = null
        recordingText = null
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
            clearTapHighlight()
            return
        }
        val eventPackage = event.packageName?.toString()
        if (isRecording && !isRecordingPaused && eventPackage == selectedPackage &&
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            recordManualTap(event, selectedPackage)
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (eventPackage == selectedPackage) { if ((isRecording && panelMinimizedForRecording) || isAiTesting) showRecordingChip() else showFloatingPanel() }
            else if (!eventPackage.isNullOrBlank()) removeFloatingPanel()
            return
        }
        if (eventPackage == selectedPackage) { if (isRecording && panelMinimizedForRecording) showRecordingChip() else showFloatingPanel() }
    }

    private fun recordManualTap(event: AccessibilityEvent, selectedPackage: String) {
        val source = event.source
        var label = ""
        var className = event.className?.toString()?.substringAfterLast('.').orEmpty()
        var resourceId = ""
        val bounds = Rect()
        try {
            if (source != null) {
                if (source.packageName?.toString() != selectedPackage) return
                if (source.isEditable || source.isPassword) {
                    statusText?.text = "Editable control tap observed; value was not recorded."
                    return
                }
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
                    statusText?.text = "Potentially sensitive action omitted from recording."
                    return
                }
                label = nodeLabel(source)
                className = source.className?.toString()?.substringAfterLast('.').orEmpty()
                resourceId = source.viewIdResourceName.orEmpty()
                source.getBoundsInScreen(bounds)
            } else {
                // Some apps emit a click event without a source node. Capture only the event's
                // non-editable click label and class; never read editable text or field contents.
                label = event.text?.joinToString(" ")?.trim().orEmpty()
                if (ScanBugSafetyPolicy.isPotentiallyDestructive(label)) {
                    skippedSensitiveSteps++
                    statusText?.text = "Potentially sensitive action omitted from recording."
                    return
                }
            }
            label = label.ifBlank { className.ifBlank { "Unlabelled control" } }
                .replace(Regex("\\s+"), " ").take(100)
            val number = recordedSteps.size + 1
            recordedSteps.add(
                RecordedStep(
                    action = "$number. Tap: $label",
                    controlType = className.ifBlank { "Unknown" },
                    resourceId = resourceId.ifBlank { "not exposed" },
                    bounds = if (bounds.isEmpty) "not exposed" else bounds.flattenToString()
                )
            )
            if (!bounds.isEmpty) showTapHighlight(bounds)
            updateRecordingUi()
            statusText?.text = "Captured action $number: $label"
        } finally {
            source?.recycle()
        }
    }

    private fun stopRecording() {
        isRecording = false
        isRecordingPaused = false
        panelMinimizedForRecording = false
        removeRecordingChip()
        updateRecordingUi()
        clearTapHighlight()
        inventoryText?.text = if (recordedSteps.isEmpty()) {
            "No manual taps recorded. Start recording, then perform actions yourself in the target app."
        } else {
            "RECORDED TEST FLOW — " + recordedSteps.size + " steps\n\n" +
                recordedSteps.mapIndexed { index, step -> (index + 1).toString() + ". " + step.action + " | " + step.result }.joinToString("\n") +
                if (skippedSensitiveSteps > 0) "\n\n" + skippedSensitiveSteps +
                    " sensitive/destructive action(s) omitted." else ""
        }
        statusText?.text = "Recording stopped. ${recordedSteps.size} actions captured; ScanBug did not execute any actions."
        showFloatingPanel()
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
        val report = "ScanBug Manual Test Flow\n" +
            "Target package: " + targetPackage + "\n" +
            "Steps: " + recordedSteps.size + "\n" +
            "Sensitive/destructive actions omitted: " + skippedSensitiveSteps + "\n" +
            "Privacy: editable field values and password values are not recorded.\n" +
            "Execution: manual user actions only; ScanBug did not replay or perform actions.\n\n" +
            recordedSteps.mapIndexed { index, step -> formatStep(index + 1, step) }.joinToString("\n\n")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "ScanBug Manual Test Flow")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching {
            startActivity(Intent.createChooser(send, "Share ScanBug test flow").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { statusText?.text = "Could not open share sheet: " + it.message }
    }


    private fun previewNextReplayStep() {
        if (isRecording || isAiTesting) { statusText?.text = "Stop the current run before replay."; return }
        if (recordedSteps.isEmpty()) { statusText?.text = "Load or record a test case first."; return }
        if (replayIndex >= recordedSteps.size) { statusText?.text = "Replay reached the end. Reset to start again."; return }
        val step = recordedSteps[replayIndex]
        val targetPackage = prefs.getString(KEY_TARGET_PACKAGE, null).orEmpty()
        if (targetPackage.isBlank()) { statusText?.text = "Select a target app first."; return }
        if (step.resourceId.isBlank() || step.resourceId == "not exposed") {
            statusText?.text = "This step has no stable resource ID. Replay skipped; perform it manually."
            replayIndex++
            return
        }
        if (step.result == "BLOCKED" || ScanBugSafetyPolicy.isPotentiallyDestructive(step.action)) {
            statusText?.text = "Safety guard blocked this step. No action performed."
            replayIndex++
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Approve one replay action?")
            .setMessage("Target: $targetPackage\nStep ${replayIndex + 1}/${recordedSteps.size}: ${step.action}\n\nScanBug will locate the exact resource ID or accessible label and request one click only. No typing, scrolling, swiping or automatic continuation. Approve only for an authorized test app.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Approve & click once") { _, _ -> executeApprovedReplayStep(step, targetPackage) }
            .show()
    }

    private fun executeApprovedReplayStep(step: RecordedStep, targetPackage: String) {
        val root = targetRoot()
        if (root == null || root.packageName?.toString() != targetPackage) {
            root?.recycle()
            statusText?.text = "Target app is not active. No action performed."
            return
        }
        val target = findReplayTarget(root, step, 0)
        root.recycle()
        if (target == null) {
            statusText?.text = "No exact enabled clickable match found. No action performed."
            return
        }
        try {
            if (target.packageName?.toString() != targetPackage || !target.isEnabled ||
                !target.isVisibleToUser || !target.isClickable ||
                ScanBugSafetyPolicy.isPotentiallyDestructive(nodeLabel(target))) {
                statusText?.text = "Replay safety check failed. No action performed."
                return
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                step.replayed = true
                replayIndex++
                statusText?.text = "One click requested. Inspect the app before approving the next step."
            } else {
                statusText?.text = "Android declined the click. Replay stopped."
            }
        } finally { target.recycle() }
    }

    private fun findReplayTarget(node: AccessibilityNodeInfo, step: RecordedStep, depth: Int): AccessibilityNodeInfo? {
        if (depth > MAX_DEPTH) return null
        val expectedId = step.resourceId
        val matches = expectedId.isNotBlank() &&
            expectedId != "not exposed" &&
            node.viewIdResourceName.orEmpty() == expectedId
        if (matches && node.packageName?.toString() == prefs.getString(KEY_TARGET_PACKAGE, null)) {
            var candidate: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(node)
            var climb = 0
            while (candidate != null && climb < 6 &&
                !(candidate.isClickable && candidate.isEnabled && candidate.isVisibleToUser)) {
                val parent = candidate.parent
                candidate.recycle()
                candidate = parent
                climb++
            }
            if (candidate != null && candidate.isClickable && candidate.isEnabled && candidate.isVisibleToUser) {
                var ancestor: AccessibilityNodeInfo? = AccessibilityNodeInfo.obtain(candidate)
                var blocked = false
                var level = 0
                while (ancestor != null && level < 6) {
                    if (ScanBugSafetyPolicy.isPotentiallyDestructive(nodeLabel(ancestor))) blocked = true
                    val parent = ancestor.parent
                    ancestor.recycle()
                    ancestor = parent
                    level++
                }
                if (!blocked) return candidate
                candidate.recycle()
            } else candidate?.recycle()
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findReplayTarget(child, step, depth + 1)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun runVisibleTextAssertions() {
        if (isRecording) {
            statusText?.text = "Stop recording before running checks."
            return
        }
        val pending = recordedSteps.filter { it.expected.isNotBlank() }
        if (pending.isEmpty()) {
            statusText?.text = "Add expected text in Review assertions first."
            return
        }
        val targetPackage = prefs.getString(KEY_TARGET_PACKAGE, null).orEmpty()
        val root = targetRoot()
        if (root == null || root.packageName?.toString() != targetPackage) {
            root?.recycle()
            statusText?.text = "Selected target screen is not active. No checks were run."
            return
        }
        val visibleText = mutableListOf<String>()
        collectSafeVisibleText(root, visibleText, 0)
        root.recycle()
        var passed = 0
        var failed = 0
        pending.forEach { step ->
            val found = visibleText.any { it.contains(step.expected.trim(), ignoreCase = true) }
            step.result = if (found) "PASS" else "FAIL"
            step.actual = if (found) "Expected text found on current screen" else "Expected text not found on current screen"
            if (found) passed++ else failed++
        }
        inventoryText?.text = renderTestCase()
        statusText?.text = "Visible-text checks: $passed PASS, $failed FAIL. This checks only current screen text, not business outcomes."
    }

    private fun collectSafeVisibleText(node: AccessibilityNodeInfo, output: MutableList<String>, depth: Int) {
        if (depth > MAX_DEPTH || output.size >= MAX_CONTROLS * 5) return
        if (node.isVisibleToUser && !node.isEditable && !node.isPassword) {
            val label = nodeLabel(node).trim()
            if (label.isNotBlank()) output.add(label)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSafeVisibleText(child, output, depth + 1)
            child.recycle()
        }
    }

    private fun reviewAssertions() {
        if (isRecording || recordedSteps.isEmpty()) {
            statusText?.text = "Stop recording and capture steps before reviewing."
            return
        }
        showAssertionEditor(0)
    }

    private fun showAssertionEditor(index: Int) {
        if (index >= recordedSteps.size) {
            inventoryText?.text = renderTestCase()
            statusText?.text = "Review complete. Results are manually entered, not automatically verified."
            return
        }
        val step = recordedSteps[index]
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 8)
        }
        form.addView(TextView(this).apply {
            text = "Step " + (index + 1) + ": " + step.action + "\nControl: " + step.controlType
        })
        val expected = EditText(this).apply {
            hint = "Expected result"
            setText(step.expected)
            minLines = 2
        }
        val actual = EditText(this).apply {
            hint = "Actual result observed"
            setText(step.actual)
            minLines = 2
        }
        form.addView(expected)
        form.addView(actual)
        form.addView(TextView(this).apply { text = "Manual result" })
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        listOf("NOT RUN", "PASS", "FAIL", "BLOCKED").forEach { value ->
            group.addView(RadioButton(this).apply {
                text = value
                tag = value
                isChecked = step.result == value
            })
        }
        form.addView(group)
        AlertDialog.Builder(this)
            .setTitle("Expected result " + (index + 1) + "/" + recordedSteps.size)
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Skip") { _, _ -> showAssertionEditor(index + 1) }
            .setPositiveButton("Save & next") { _, _ ->
                step.expected = expected.text.toString().trim()
                step.actual = actual.text.toString().trim()
                step.result = group.findViewById<RadioButton>(group.checkedRadioButtonId)
                    ?.tag?.toString() ?: "NOT RUN"
                showAssertionEditor(index + 1)
            }
            .show()
    }

    private fun formatStep(number: Int, step: RecordedStep): String {
        return number.toString() + ". " + step.action + "\n" +
            "   Control: " + step.controlType + " | Resource ID: " + step.resourceId +
            " | Bounds: " + step.bounds + "\n" +
            "   Expected: " + step.expected.ifBlank { "Not specified" } + "\n" +
            "   Actual: " + step.actual.ifBlank { "Not entered" } + "\n" +
            "   Result: " + step.result
    }

    private fun renderTestCase(): String {
        return "SCANBUG TEST CASE\nTarget: " +
            prefs.getString(KEY_TARGET_PACKAGE, "unknown") + "\nSteps: " + recordedSteps.size + "\n" +
            "PASS: " + recordedSteps.count { it.result == "PASS" } +
            " | FAIL: " + recordedSteps.count { it.result == "FAIL" } +
            " | BLOCKED: " + recordedSteps.count { it.result == "BLOCKED" } +
            " | NOT RUN: " + recordedSteps.count { it.result == "NOT RUN" } + "\n\n" +
            recordedSteps.mapIndexed { index, step -> formatStep(index + 1, step) }.joinToString("\n\n") +
            "\n\nResults are manually entered by the tester; ScanBug has not automatically asserted app behavior."
    }

    private fun saveReusableTestCase() {
        if (isRecording || recordedSteps.isEmpty()) {
            statusText?.text = "Stop recording and capture steps before saving."
            return
        }
        val titleInput = EditText(this).apply {
            hint = "Test case title"
            setText("Manual test — " + prefs.getString(KEY_TARGET_PACKAGE, "app"))
        }
        AlertDialog.Builder(this)
            .setTitle("Save reusable test case")
            .setMessage("Saves this checklist and expected results locally on this phone.")
            .setView(titleInput)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val json = org.json.JSONObject().apply {
                    put("title", titleInput.text.toString().trim().ifBlank { "ScanBug test case" })
                    put("targetPackage", prefs.getString(KEY_TARGET_PACKAGE, ""))
                    put("steps", org.json.JSONArray().apply {
                        recordedSteps.forEach { step ->
                            put(org.json.JSONObject().apply {
                                put("action", step.action)
                                put("controlType", step.controlType)
                                put("resourceId", step.resourceId)
                                put("bounds", step.bounds)
                                put("expected", step.expected)
                                put("actual", step.actual)
                                put("result", step.result)
                            })
                        }
                    })
                }
                prefs.edit().putString(KEY_SAVED_TEST_CASE, json.toString()).apply()
                inventoryText?.text = renderTestCase()
                statusText?.text = "Test case saved locally on this phone."
            }
            .show()
    }

    private fun loadSavedTestCase() {
        val raw = prefs.getString(KEY_SAVED_TEST_CASE, null)
        if (raw.isNullOrBlank()) {
            statusText?.text = "No saved test case on this phone."
            return
        }
        runCatching {
            val json = org.json.JSONObject(raw)
            val steps = json.getJSONArray("steps")
            recordedSteps.clear()
            replayIndex = 0
            for (i in 0 until steps.length()) {
                val item = steps.getJSONObject(i)
                recordedSteps.add(
                    RecordedStep(
                        action = item.optString("action"),
                        controlType = item.optString("controlType"),
                        resourceId = item.optString("resourceId"),
                        bounds = item.optString("bounds"),
                        expected = item.optString("expected"),
                        actual = item.optString("actual"),
                        result = item.optString("result", "NOT RUN")
                    )
                )
            }
            inventoryText?.text = "SAVED TEST CASE: " + json.optString("title") + "\n\n" + renderTestCase()
            statusText?.text = "Loaded " + recordedSteps.size + " saved steps."
        }.onFailure {
            statusText?.text = "Could not load saved test case: " + it.message
        }
    }


    private fun saveExecutionRun() {
        if (isRecording || recordedSteps.isEmpty()) {
            statusText?.text = "Stop recording and capture a test case before saving a run."
            return
        }
        val history = runCatching { org.json.JSONArray(prefs.getString(KEY_RUN_HISTORY, "[]") ?: "[]") }
            .getOrElse { org.json.JSONArray() }
        val entry = org.json.JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("targetPackage", prefs.getString(KEY_TARGET_PACKAGE, "unknown"))
            put("stepCount", recordedSteps.size)
            put("pass", recordedSteps.count { it.result == "PASS" })
            put("fail", recordedSteps.count { it.result == "FAIL" })
            put("blocked", recordedSteps.count { it.result == "BLOCKED" })
            put("notRun", recordedSteps.count { it.result == "NOT RUN" })
            put("report", renderTestCase())
        }
        val updated = org.json.JSONArray().apply {
            put(entry)
            for (i in 0 until minOf(history.length(), 19)) put(history.getJSONObject(i))
        }
        prefs.edit().putString(KEY_RUN_HISTORY, updated.toString()).apply()
        statusText?.text = "Run saved locally. History contains ${updated.length()} runs."
    }

    private fun shareExecutionHistoryHtml() {
        val history = runCatching { org.json.JSONArray(prefs.getString(KEY_RUN_HISTORY, "[]") ?: "[]") }
            .getOrElse { org.json.JSONArray() }
        if (history.length() == 0) {
            statusText?.text = "No saved execution history. Save a run first."
            return
        }
        fun escapeHtml(value: String): String = value.replace("&", "&amp;")
            .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val rows = (0 until history.length()).joinToString("") { index ->
            val item = history.getJSONObject(index)
            val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(item.optLong("timestamp")))
            "<section><h2>Run ${index + 1} — ${escapeHtml(date)}</h2>" +
                "<p><b>Target:</b> ${escapeHtml(item.optString("targetPackage"))} | " +
                "<b>Steps:</b> ${item.optInt("stepCount")} | PASS: ${item.optInt("pass")} | " +
                "FAIL: ${item.optInt("fail")} | BLOCKED: ${item.optInt("blocked")} | " +
                "NOT RUN: ${item.optInt("notRun")}</p><pre>${escapeHtml(item.optString("report"))}</pre></section>"
        }
        val html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">" +
            "<title>ScanBug execution history</title><style>body{font:14px sans-serif;margin:24px;color:#222}" +
            "section{border:1px solid #bbb;padding:16px;margin:16px 0}pre{white-space:pre-wrap;overflow-wrap:anywhere}" +
            "</style></head><body><h1>ScanBug execution history</h1>" +
            "<p>Generated locally on request. Editable field contents and passwords are excluded.</p>" +
            rows + "</body></html>"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/html"
            putExtra(Intent.EXTRA_SUBJECT, "ScanBug execution history")
            putExtra(Intent.EXTRA_TEXT, html)
        }
        runCatching { startActivity(Intent.createChooser(send, "Share ScanBug HTML report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { statusText?.text = "Could not open report share sheet: " + it.message }
    }

    private fun startAiTesting() {
        if (isRecording) {
            statusText?.text = "Stop manual recording before starting AI testing."
            return
        }
        val target = prefs.getString(KEY_TARGET_PACKAGE, null).orEmpty()
        val root = targetRoot()
        if (target.isBlank() || root == null || root.packageName?.toString() != target) {
            root?.recycle()
            statusText?.text = "Open the selected target app first."
            return
        }
        root.recycle()
        isAiTesting = true
        aiActionCount = 0
        aiVisited.clear()
        aiResults.clear()
        panelMinimizedForRecording = true
        statusText?.text = "AI runtime started. Exploring safe, labelled navigation controls only."
        removeFloatingPanel()
        showRecordingChip()
        aiHandler.post { runAiTestingStep() }
    }

    private fun stopAiTesting(reason: String) {
        aiHandler.removeCallbacksAndMessages(null)
        isAiTesting = false
        panelMinimizedForRecording = false
        removeRecordingChip()
        clearTapHighlight()
        showFloatingPanel()
        statusText?.text = "AI testing finished: $aiActionCount safe actions. $reason"
        inventoryText?.text = buildString {
            append("AI TEST RUN · ").append(aiActionCount).append(" actions\n")
            if (aiResults.isEmpty()) append("No safe, labelled navigation controls were available.\n")
            aiResults.forEachIndexed { i, result -> append(i + 1).append(". ").append(result).append("\n") }
            append("\nOnly low-risk navigation was automated. Inputs, authentication, transactions and destructive actions were not executed.")
        }
    }

    private fun runAiTestingStep() {
        if (!isAiTesting) return
        val target = prefs.getString(KEY_TARGET_PACKAGE, null).orEmpty()
        val root = targetRoot()
        if (root == null || root.packageName?.toString() != target) {
            root?.recycle()
            stopAiTesting("Target app screen unavailable; tester intervention is needed.")
            return
        }
        val candidate = findNextSafeCandidate(root, "root")
        root.recycle()
        if (candidate == null || aiActionCount >= 8) {
            stopAiTesting(if (aiActionCount >= 8) "Reached the safe exploration limit." else "No more safe navigation controls found.")
            return
        }
        val liveRoot = targetRoot()
        if (liveRoot == null || liveRoot.packageName?.toString() != target) {
            liveRoot?.recycle()
            stopAiTesting("Target app changed before action.")
            return
        }
        val clicked = performCandidateClick(liveRoot, "root", candidate)
        liveRoot.recycle()
        if (!clicked) {
            aiVisited.add(candidate.key)
            aiHandler.postDelayed({ runAiTestingStep() }, 250)
            return
        }
        aiVisited.add(candidate.key)
        aiActionCount++
        aiResults.add("Tapped '" + candidate.label + "' · " + candidate.resourceId)
        updateRecordingChip()
        statusText?.text = "AI testing: $aiActionCount safe actions explored."
        aiHandler.postDelayed({ runAiTestingStep() }, 900)
    }

    private data class AiCandidate(val key: String, val label: String, val resourceId: String)

    private fun findNextSafeCandidate(node: AccessibilityNodeInfo, path: String): AiCandidate? {
        if (node.packageName?.toString() != prefs.getString(KEY_TARGET_PACKAGE, null)) return null
        val label = nodeLabel(node).take(100)
        val className = node.className?.toString().orEmpty()
        val resourceId = node.viewIdResourceName.orEmpty()
        val key = if (resourceId.isNotBlank()) resourceId else "$path|$className|$label"
        val safeLabel = label.isNotBlank() && !node.isEditable && !node.isPassword &&
            !node.isCheckable && !node.isLongClickable && !node.isScrollable &&
            node.isVisibleToUser && node.isEnabled && node.isClickable &&
            !ScanBugSafetyPolicy.isPotentiallyDestructive(label) &&
            !Regex("(?i)\\b(login|sign in|sign up|log out|logout|account|profile|checkout|cart|book|reserve|call|email|share|settings|permission|install|uninstall)\\b").containsMatchIn(label) &&
            Regex("(?i)\\b(home|back|menu|tab|search|filter|category|details|view|expand|collapse|next|previous|more|close panel)\\b").containsMatchIn(label)
        if (safeLabel && !aiVisited.contains(key)) return AiCandidate(key, label, resourceId)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNextSafeCandidate(child, "$path/$i")
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun performCandidateClick(node: AccessibilityNodeInfo, path: String, candidate: AiCandidate): Boolean {
        if (node.packageName?.toString() != prefs.getString(KEY_TARGET_PACKAGE, null)) return false
        val label = nodeLabel(node).take(100)
        val resourceId = node.viewIdResourceName.orEmpty()
        val key = if (resourceId.isNotBlank()) resourceId else "$path|" + node.className?.toString().orEmpty() + "|$label"
        if (key == candidate.key && node.isVisibleToUser && node.isEnabled && node.isClickable &&
            !node.isEditable && !node.isPassword && !ScanBugSafetyPolicy.isPotentiallyDestructive(label)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            showTapHighlight(bounds)
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val done = performCandidateClick(child, "$path/$i", candidate)
            child.recycle()
            if (done) return true
        }
        return false
    }

    private fun showAiSuggestions() {
        val targetPackage = prefs.getString(KEY_TARGET_PACKAGE, null).orEmpty()
        val root = targetRoot()
        if (root == null || root.packageName?.toString() != targetPackage) {
            root?.recycle()
            statusText?.text = "Open the selected app screen before asking for test suggestions."
            return
        }
        val controls = mutableListOf<ControlInfo>()
        collectControls(root, controls, 0, "root")
        root.recycle()
        if (controls.isEmpty()) {
            statusText?.text = "No accessible controls found. Suggestions may be incomplete for custom-drawn or protected screens."
            return
        }
        val suggestions = LocalTestAdvisor.analyze(controls.map {
            LocalTestAdvisor.UiSignal(
                label = it.label.take(100),
                type = it.type,
                category = it.category,
                enabled = it.enabled,
                clickable = it.clickable
            )
        })
        val names = suggestions.map { "${it.priority} · ${it.title}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("AI Test Advisor · ${suggestions.size} suggestions")
            .setItems(names) { _, index ->
                val item = suggestions[index]
                val detail = item.rationale + "\n\nSuggested placeholder steps:\n" +
                    item.placeholderSteps.mapIndexed { i, step -> "${i + 1}. $step" }.joinToString("\n") +
                    "\n\nExpected result:\n" + item.expected +
                    "\n\nThese are heuristic suggestions from accessible UI metadata, not verified app requirements."
                AlertDialog.Builder(this)
                    .setTitle(item.title)
                    .setMessage(detail)
                    .setNegativeButton("Close", null)
                    .setPositiveButton("Add placeholder") { _, _ ->
                        recordedSteps.add(
                            RecordedStep(
                                action = "AI suggestion: ${item.title}",
                                controlType = "AI test placeholder",
                                resourceId = "not exposed",
                                bounds = "not exposed",
                                expected = item.expected
                            )
                        )
                        inventoryText?.text = renderTestCase()
                        statusText?.text = "Added '${item.title}' as an editable NOT RUN placeholder."
                        updateRecordingUi()
                    }
                    .show()
            }
            .setNegativeButton("Close", null)
            .setNeutralButton("Refresh analysis") { _, _ -> showAiSuggestions() }
            .show()
        statusText?.text = "Local analysis complete. No screen content was sent to a server."
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
        clearTapHighlight()
        aiHandler.removeCallbacksAndMessages(null)
        isAiTesting = false
        removeRecordingChip()
        removeFloatingPanel()
        super.onDestroy()
    }

    companion object {
        const val PREFS = "scanbug_local"
        const val KEY_TARGET_PACKAGE = "selected_target_package"
        const val KEY_SAVED_TEST_CASE = "saved_reusable_test_case"
        private const val KEY_RUN_HISTORY = "local_execution_history"
        private const val MAX_CONTROLS = 100
        private const val MAX_DEPTH = 20
    }
}

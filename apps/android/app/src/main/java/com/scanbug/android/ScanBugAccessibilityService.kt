package com.scanbug.android

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ScanBugAccessibilityService : AccessibilityService() {
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private var tapReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        tapReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val requested = intent?.getStringExtra(EXTRA_TEXT)?.trim().orEmpty()
                if (requested.isNotEmpty()) tapVisibleLabel(requested)
            }
        }
        registerReceiver(tapReceiver, IntentFilter(ACTION_TAP_TEXT), Context.RECEIVER_NOT_EXPORTED)
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

    private fun tapVisibleLabel(requested: String) {
        val root = rootInActiveWindow
        if (root == null) {
            Toast.makeText(this, "No active app window. Open the target app first.", Toast.LENGTH_SHORT).show()
            return
        }
        val matches = root.findAccessibilityNodeInfosByText(requested)
        val target = matches.firstOrNull { it.isVisibleToUser && it.isEnabled && it.isClickable }
            ?: matches.firstOrNull { it.isVisibleToUser && it.isEnabled }
        val success = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        matches.forEach { it.recycle() }
        root.recycle()
        Toast.makeText(this, if (success) "Tapped: $requested" else "No clickable accessible match: $requested", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() { }

    override fun onDestroy() {
        tapReceiver?.let { runCatching { unregisterReceiver(it) } }
        tapReceiver = null
        super.onDestroy()
    }

    companion object {
        const val PREFS = "scanbug_local"
        const val KEY_SNAPSHOT = "latest_accessible_snapshot"
        const val ACTION_TAP_TEXT = "com.scanbug.android.action.TAP_TEXT"
        const val EXTRA_TEXT = "accessible_text"
    }
}

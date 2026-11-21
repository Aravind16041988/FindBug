package com.scanbug.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
 
class MainActivity : Activity() {
    private lateinit var appSpinner: Spinner
    private lateinit var snapshotText: TextView
    private lateinit var labelInput: EditText
    private val prefs by lazy { getSharedPreferences(ScanBugAccessibilityService.PREFS, Context.MODE_PRIVATE) }
    private var apps: List<LaunchableApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        content.addView(TextView(this).apply { text = "ScanBug — on-device Android QA"; textSize = 22f })
        content.addView(TextView(this).apply {
            text = "Choose an installed app, enable accessibility, open the target, then inspect its exposed UI."
            textSize = 14f
            setPadding(0, 12, 0, 16)
        })
        appSpinner = Spinner(this)
        content.addView(appSpinner)
        content.addView(actionButton("Refresh app list") { loadApps() })
        content.addView(actionButton("Enable ScanBug accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        content.addView(actionButton("Open selected app") { launchSelectedApp() })
        content.addView(actionButton("Read current screen") { showSnapshot() })
        content.addView(TextView(this).apply { text = "Tap a visible control by its exact accessible label"; textSize = 16f; setPadding(0, 20, 0, 6) })
        labelInput = EditText(this).apply { hint = "Example: Continue"; singleLine = true }
        content.addView(labelInput)
        content.addView(actionButton("Tap matching control") { requestTap() })
        snapshotText = TextView(this).apply {
            text = "Screen snapshot will appear here. Only UI information exposed through Android accessibility is available."
            textSize = 13f
            setPadding(0, 18, 0, 18)
        }
        content.addView(snapshotText)
        content.addView(actionButton("Clear local snapshot") {
            prefs.edit().remove(ScanBugAccessibilityService.KEY_SNAPSHOT).apply()
            snapshotText.text = "Local snapshot cleared."
        })
        loadApps()
    }

    override fun onResume() { super.onResume(); if (::snapshotText.isInitialized) showSnapshot() }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .map { LaunchableApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        appSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, apps.map { "${it.label}  (${it.packageName})" })
        Toast.makeText(this, "Found ${apps.size} launchable apps", Toast.LENGTH_SHORT).show()
    }

    private fun launchSelectedApp() {
        val app = apps.getOrNull(appSpinner.selectedItemPosition) ?: return
        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch == null) { Toast.makeText(this, "No launch activity found", Toast.LENGTH_SHORT).show(); return }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    private fun showSnapshot() {
        val snapshot = prefs.getString(ScanBugAccessibilityService.KEY_SNAPSHOT, null)
        snapshotText.text = snapshot ?: "No screen snapshot yet. Enable the service, open a target app, and return here."
    }

    private fun requestTap() {
        val label = labelInput.text.toString().trim()
        if (label.isEmpty()) { labelInput.error = "Enter an accessible label"; return }
        sendBroadcast(Intent(ScanBugAccessibilityService.ACTION_TAP_TEXT).setPackage(packageName).putExtra(ScanBugAccessibilityService.EXTRA_TEXT, label))
        Toast.makeText(this, "Tap request sent. Check the target app for the result.", Toast.LENGTH_SHORT).show()
    }

    data class LaunchableApp(val label: String, val packageName: String)
}

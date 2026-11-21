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
    private val prefs by lazy { getSharedPreferences(ScanBugAccessibilityService.PREFS, Context.MODE_PRIVATE) }
    private var apps: List<LaunchableApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        content.addView(TextView(this).apply {
            text = "ScanBug — read-only Android UI explorer"
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = "ScanBug lists controls exposed by a selected app. It does not tap controls, enter text, take screenshots, or save screen snapshots. You remain in control of all actions."
            textSize = 14f
            setPadding(0, 12, 0, 16)
        })
        appSpinner = Spinner(this)
        content.addView(appSpinner)
        content.addView(actionButton("Refresh app list") { loadApps() })
        content.addView(actionButton("Enable ScanBug accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        content.addView(actionButton("Open selected app") { launchSelectedApp() })
        content.addView(TextView(this).apply {
            text = "When the selected app opens, use the ScanBug overlay and tap Explore screen. Inspection is read-only; interact with the target app yourself."
            textSize = 13f
            setPadding(0, 18, 0, 18)
        })
        content.addView(actionButton("Stop ScanBug session") {
            prefs.edit().remove(ScanBugAccessibilityService.KEY_TARGET_PACKAGE).apply()
            Toast.makeText(this, "ScanBug session stopped", Toast.LENGTH_SHORT).show()
        })
        loadApps()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 8 }
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(intent, 0)
            .map { LaunchableApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        appSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            apps.map { "${it.label}  (${it.packageName})" }
        )
        Toast.makeText(this, "Found ${apps.size} launchable apps", Toast.LENGTH_SHORT).show()
    }

    private fun launchSelectedApp() {
        val app = apps.getOrNull(appSpinner.selectedItemPosition) ?: return
        val launch = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launch == null) {
            Toast.makeText(this, "No launch activity found", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString(ScanBugAccessibilityService.KEY_TARGET_PACKAGE, app.packageName).apply()
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    data class LaunchableApp(val label: String, val packageName: String)
}

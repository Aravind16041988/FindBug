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
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFFF7F4EF.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        content.addView(TextView(this).apply {
            text = "ScanBug"
            textSize = 30f
            setTextColor(0xFF25231F.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "Your mobile QA companion. Explore screens, get on-device test ideas, record manual actions and review test results."
            textSize = 15f
            setTextColor(0xFF68635C.toInt())
            setPadding(0, 12, 0, 20)
        })
        appSpinner = Spinner(this)
        content.addView(appSpinner)
        content.addView(actionButton("Refresh app list") { loadApps() })
        content.addView(actionButton("Review access & enable service") { showAccessibilityDisclosure() })
        content.addView(actionButton("Open selected app") { launchSelectedApp() })
        content.addView(TextView(this).apply {
            text = "Hybrid AI runtime: local rule-based analysis works offline. Optional remote AI is not enabled in this build. UI metadata stays on this device; editable values and passwords are excluded."
            textSize = 13f
            setTextColor(0xFF68635C.toInt())
            setPadding(0, 18, 0, 18)
        })
        content.addView(actionButton("Stop ScanBug session") {
            prefs.edit().remove(ScanBugAccessibilityService.KEY_TARGET_PACKAGE).apply()
            Toast.makeText(this, "ScanBug session stopped", Toast.LENGTH_SHORT).show()
        })
        loadApps()
        if (!prefs.getBoolean(KEY_ACCESS_DISCLOSURE_ACCEPTED, false)) {
            root.post { showAccessibilityDisclosure() }
        }
    }

    private fun showAccessibilityDisclosure() {
        val message = """
            ScanBug uses Android Accessibility Service to inspect the visible interface of the app you select, including accessible control labels, descriptions and control types.

            This access can expose sensitive information displayed by that app. ScanBug uses accessible UI metadata locally for inventory, manual click recording and local test suggestions. Editable field values and password values are excluded. The app does not type text or upload inspected content. Replay, when used, requires a separate approval for each click. Inventory, test cases and reports are shared only when you explicitly request sharing.

            Enable the service only for apps you own or are authorized to test. You can decline now and enable it later from this screen. You can turn the service off at any time in Android Accessibility Settings.
        """.trimIndent()
        android.app.AlertDialog.Builder(this)
            .setTitle("Important: Accessibility access")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("I understand — continue") { _, _ ->
                prefs.edit().putBoolean(KEY_ACCESS_DISCLOSURE_ACCEPTED, true).apply()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Not now") { dialog, _ -> dialog.dismiss() }
            .show()
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

    companion object {
        private const val KEY_ACCESS_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
    }

    data class LaunchableApp(val label: String, val packageName: String)
}

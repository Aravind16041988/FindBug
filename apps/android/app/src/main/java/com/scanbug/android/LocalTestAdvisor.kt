package com.scanbug.android

/**
 * On-device test advisor. It uses only sanitized UI metadata supplied by the
 * accessibility inventory; it never reads editable values or performs actions.
 */
object LocalTestAdvisor {
    data class UiSignal(
        val label: String,
        val type: String,
        val category: String,
        val enabled: Boolean,
        val clickable: Boolean
    )

    data class Suggestion(
        val title: String,
        val rationale: String,
        val priority: String,
        val placeholderSteps: List<String>,
        val expected: String
    )

    fun analyze(signals: List<UiSignal>): List<Suggestion> {
        val safe = signals.filterNot {
            ScanBugSafetyPolicy.isPotentiallyDestructive(it.label) ||
                it.label.contains("password", true) ||
                it.label.contains("otp", true) ||
                it.label.contains("one time code", true)
        }
        val labels = safe.map { it.label.lowercase() }
        val categories = safe.map { it.category.uppercase() }
        val output = mutableListOf<Suggestion>()

        fun add(title: String, rationale: String, priority: String, steps: List<String>, expected: String) {
            output.add(Suggestion(title, rationale, priority, steps, expected))
        }

        val hasInput = categories.any { it == "INPUTS" }
        val hasButton = categories.any { it.contains("BUTTON") || it.contains("ACTION") }
        val hasSearch = labels.any { it.contains("search") || it.contains("filter") }
        val hasLogin = labels.any { it.contains("login") || it.contains("sign in") }
        val hasNavigation = categories.any { it == "TABS" } ||
            labels.any { it.contains("menu") || it.contains("back") || it.contains("home") }

        if (hasInput) {
            add("Input validation", "Editable controls are present. Values are not inspected; validate them manually with test data.",
                "HIGH", listOf("Leave a required field empty", "Enter an invalid format using approved test data", "Enter a valid boundary value"),
                "The app should show appropriate validation and accept valid input.")
            add("Input usability", "Check field labels, keyboard type, focus order and masking without recording entered values.",
                "MEDIUM", listOf("Move focus between fields", "Check keyboard type and field hints", "Verify sensitive fields are masked"),
                "Fields should be understandable, correctly focused and protect sensitive values.")
        }
        if (hasButton) {
            add("Primary action behaviour", "Clickable actions are exposed on this screen.",
                "HIGH", listOf("Check action with valid preconditions", "Check repeated tap behaviour", "Check disabled or loading state where applicable"),
                "The action should produce one intended outcome and prevent unintended duplicate submissions.")
        }
        if (hasSearch) {
            add("Search and filtering", "Search or filter controls were identified by their accessible labels.",
                "HIGH", listOf("Search for a known result", "Search for a term with no results", "Try empty and special-character queries", "Clear filters and verify the result list"),
                "Results should match the query and empty states should be clear.")
        }
        if (hasLogin) {
            add("Authentication negative paths", "A login-related control appears in the accessible UI.",
                "HIGH", listOf("Use an approved valid test account", "Try an invalid credential in a test environment", "Check empty-field validation", "Verify logout/session expiry separately"),
                "Authentication should accept authorized credentials and reject invalid attempts safely.")
        }
        if (hasNavigation) {
            add("Navigation consistency", "Tabs or navigation-related controls are exposed.",
                "MEDIUM", listOf("Open each visible tab", "Navigate forward and back", "Verify selected tab and screen title", "Check state after returning to a screen"),
                "Navigation should open the intended screen and preserve expected state.")
        }
        if (safe.any { it.type.contains("checkbox", true) || it.type.contains("switch", true) || it.type.contains("radio", true) }) {
            add("Selection controls", "Checkbox, switch or radio-style controls were detected.",
                "MEDIUM", listOf("Toggle the control", "Verify selected state", "Navigate away and return to inspect persistence"),
                "Selection state should update visibly and persist according to product requirements.")
        }
        add("Accessibility and layout review", "A visible UI inventory is available for a manual accessibility pass.",
            "MEDIUM", listOf("Check meaningful labels for interactive controls", "Check focus order and touch target visibility", "Check content at larger font scale"),
            "Interactive controls should be identifiable and usable without clipped content.")

        return output.distinctBy { it.title }.take(12)
    }
}

package com.scanbug.android

import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTestAdvisorTest {
    @Test
    fun suggestsInputValidationWithoutReadingValues() {
        val suggestions = LocalTestAdvisor.analyze(
            listOf(LocalTestAdvisor.UiSignal("Email", "Text input", "INPUTS", true, false))
        )
        assertTrue(suggestions.any { it.title == "Input validation" })
    }

    @Test
    fun suggestsSearchScenariosFromAccessibleLabels() {
        val suggestions = LocalTestAdvisor.analyze(
            listOf(LocalTestAdvisor.UiSignal("Search", "Button", "BUTTONS & ACTIONS", true, true))
        )
        assertTrue(suggestions.any { it.title == "Search and filtering" })
    }

    @Test
    fun excludesSensitiveLabelsFromAnalysisSignals() {
        val suggestions = LocalTestAdvisor.analyze(
            listOf(
                LocalTestAdvisor.UiSignal("Password", "Text input", "INPUTS", true, false),
                LocalTestAdvisor.UiSignal("Transfer money", "Button", "BUTTONS & ACTIONS", true, true)
            )
        )
        assertTrue(suggestions.none { it.title == "Input validation" })
    }
}

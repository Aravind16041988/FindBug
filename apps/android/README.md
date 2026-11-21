# ScanBug Android — standalone QA proof of concept

This is an early native Android proof of concept, separate from the existing React/Vite + Express + Playwright application.

## Implemented in this POC

- Lists launchable apps visible to Android package-visibility rules.
- Opens a user-selected installed app.
- Uses a user-enabled Android Accessibility Service to read the target screen's exposed accessibility tree (text, content descriptions, and basic clickable/editable/enabled flags).
- Lets the user request a tap on a visible control by its accessible text label.
- Stores the latest text snapshot locally in app preferences.

## Not implemented yet

This is not yet an autonomous AI tester. It is the first device-only interaction spike: app selection, UI inspection, and a user-requested tap. It does not yet record/replay journeys, take screenshots, run exploratory AI, detect crashes comprehensively, or generate formal reports.

Android restricts cross-app control. The user must explicitly enable the ScanBug Accessibility Service in Android Settings. Only UI exposed by the target app is discoverable; custom-drawn controls, protected screens, and unlabeled elements may not work. This app does not read target-app private files, memory, credentials, or source code. Test only apps you own or are authorized to test. Never automate payments, transfers, deletion, or other destructive actions.

## Build with Android Studio

1. Install Android Studio with Android SDK Platform 35 and a compatible JDK 17.
2. Open the apps/android folder as an Android Studio project.
3. Allow Gradle sync to download the Android Gradle Plugin and Kotlin plugin.
4. Connect an Android phone with USB debugging enabled, or use an emulator.
5. Run the app configuration.
6. In ScanBug, tap Enable ScanBug accessibility and enable the service in Android Settings.
7. Select an app, tap Open selected app, interact with its screen, then return to ScanBug and tap Read current screen.
8. Enter an exact visible accessible label (for example, Continue) and tap Tap matching control. The service attempts the click in the active app window.

## Next milestones

1. Improve target-app scoping and accessible node matching (exact match, duplicate handling, parent clickable resolution).
2. Add a bounded action recorder/replayer and visible stop control.
3. Add screenshot evidence only through Android's user-consented MediaProjection flow.
4. Add local run history and HTML/JSON reports.
5. Add guided exploration with explicit approval, step/time limits, and a safe-action allowlist.
6. Evaluate optional on-device AI after deterministic automation is reliable.

## Privacy

No network permission, analytics, cloud service, or external AI provider is included. The UI snapshot is stored locally and can be cleared from the app.

# ScanBug Android — standalone QA proof of concept

This is a native Kotlin Android companion to the existing React/Vite + Express + Playwright ScanBug application. It is designed to inspect and interact with a user-selected installed app directly on the phone.

## Implemented

- Lists launchable apps visible under Android package-visibility rules.
- Opens a user-selected installed app and scopes the ScanBug session to that package.
- Uses the Android Accessibility Service, enabled explicitly by the user, to inspect the target app's exposed UI tree.
- Shows a visible ScanBug overlay only while the selected target app is active.
- **Explore screen** performs a read-only inventory of visible, enabled clickable and editable controls, with labels and control types.
- Displays a warning marker for labels that look sensitive or destructive (for example, payment, transfer, delete, purchase, submit, or sign out).
- Allows a user to request an exact-label tap manually. The local safety guard blocks labels matching the sensitive/destructive keyword list.
- Stores the latest accessible text snapshot locally in app preferences.

## Current limitations

This is a deterministic UI inspection and manual interaction POC, not an autonomous AI tester. It does not yet record/replay journeys, take screenshots, automatically navigate flows, assert expected outcomes, comprehensively detect crashes, or generate formal reports.

The keyword guard is intentionally conservative but is not a complete safety system: labels can be ambiguous, hidden behind icons, localized, or worded differently. Review the target screen before every manual action. ScanBug does not tap controls automatically during exploration.

Android restricts cross-app control. The user must explicitly enable the ScanBug Accessibility Service in Android Settings. Only UI exposed by the target app is discoverable; custom-drawn controls, protected screens, and unlabeled elements may not work. This app does not read target-app private files, memory, credentials, or source code. Test only apps you own or are authorized to test. Do not use it for payments, transfers, purchases, deletion, or other destructive actions.

## Build with Android Studio

1. Install Android Studio with Android SDK Platform 35 and a compatible JDK 17.
2. Open the `apps/android` folder as an Android Studio project.
3. Allow Gradle sync to download the Android Gradle Plugin and Kotlin plugin.
4. Connect an Android phone with USB debugging enabled, or use an emulator.
5. Run the app configuration.
6. In ScanBug, tap **Enable ScanBug accessibility** and enable the service in Android Settings.
7. Return to ScanBug, select an app, and tap **Open selected app**.
8. In the target app, use **Explore screen** on the ScanBug overlay to list accessible controls. This scan is read-only.
9. To test a control, enter its exact accessible label and tap **Tap label**. Sensitive/destructive keyword matches are blocked by the local guard.
10. Return to ScanBug and tap **Read latest screen snapshot** to inspect the captured accessible labels.

## Next milestones

1. Add a bounded, user-approved action recorder/replayer with a visible stop control.
2. Add explicit test steps and assertions (for example, verify a label or screen state after an action).
3. Add screenshot evidence only through Android's user-consented MediaProjection flow.
4. Add local run history and HTML/JSON reports.
5. Add guided multi-screen exploration with explicit approval, step/time limits, and a stronger safe-action policy.
6. Evaluate optional on-device AI only after deterministic automation is reliable.

## Privacy

No network permission, analytics, cloud service, or external AI provider is included. UI snapshots remain on-device in app preferences and can be cleared from the app.

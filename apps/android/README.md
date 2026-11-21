# ScanBug Android — read-only UI explorer POC

A native Kotlin Android companion to ScanBug. It can inspect the accessibility tree exposed by a user-selected installed app directly on the phone.

## Implemented

- Lists launchable apps visible under Android package-visibility rules.
- Opens a user-selected app and scopes the ScanBug session to that package.
- Uses Android Accessibility Service, enabled explicitly by the user, to inspect the selected app's exposed UI tree.
- Shows a visible ScanBug overlay only while the selected target app is active.
- **Explore screen** lists visible, enabled clickable and editable controls with their accessible labels and control types.
- Password values and editable field contents are never read for display or stored.
- No screen snapshot, target-app text, or UI inventory is persisted.
- The overlay provides only **Explore screen** and **Stop**. There is no tap, text-entry, scroll, gesture, or replay feature.

## Safety and privacy boundaries

This build is intentionally read-only. ScanBug does not invoke click actions, enter text, perform gestures, or automatically navigate the target app. Any interaction must be performed manually by the user in the target app.

The Accessibility Service can inspect UI content exposed by the selected app while the service is enabled. This is inherently sensitive Android access even though this POC does not store screen contents or transmit them. Android/Google Play Protect may still warn about sideloaded apps that declare an Accessibility Service; a read-only implementation does not guarantee Play Protect approval.

No network permission, analytics, cloud service, external AI provider, screenshots, or local UI-history storage is included. Test only apps you own or are authorized to test. Do not use the POC with sensitive personal or financial apps.

## Limitations

This is not a complete mobile automation tester. It does not record/replay journeys, take screenshots, perform actions, assert expected outcomes, comprehensively detect crashes, or generate formal reports. Only UI exposed through Android accessibility APIs can be discovered; custom-drawn, protected, or unlabeled controls may not appear.

## Build with Android Studio

1. Install Android Studio with Android SDK Platform 35 and JDK 17.
2. Open the `apps/android` folder as an Android Studio project.
3. Allow Gradle sync to download the Android Gradle Plugin and Kotlin plugin.
4. Connect an Android phone with USB debugging enabled, or use an emulator.
5. Build and run the app.
6. In ScanBug, tap **Enable ScanBug accessibility** and enable the service in Android Settings.
7. Return to ScanBug, select an app, and tap **Open selected app**.
8. In the target app, tap **Explore screen** on the ScanBug overlay. Inspection is read-only.
9. Tap **Stop** on the overlay or **Stop ScanBug session** in ScanBug to end the session.

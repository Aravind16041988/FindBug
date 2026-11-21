# ScanBug Android 0.9.0 — controlled mobile QA explorer

ScanBug is a native Android QA companion for apps the tester explicitly selects and is authorized to test.

## What's implemented

- Lists launchable apps and scopes inspection to the selected package.
- Uses Android Accessibility Service only after an explicit in-app disclosure and user enablement.
- Explores accessible controls and metadata (labels, types, resource IDs, bounds, hierarchy and state).
- Manual recording captures click events from the selected app while the tester explicitly starts recording.
- Recording shows a live action count and a temporary outline around controls when Android exposes their screen bounds.
- When a click event has no source node, ScanBug may capture its non-editable event label and class metadata. Events that cannot be safely identified are not recorded.
- Sensitive/destructive action labels are filtered conservatively. This is a heuristic safeguard, not a guarantee.
- Review assertions, expected/actual results, locally saved test cases, manually approved single-step replay, local run history and HTML report sharing are supported.
- **AI Test Advisor:** on-device heuristic analysis of accessible UI metadata suggests functional, negative, navigation, input-validation, selection and accessibility test placeholders. A tester can inspect and add a suggestion to the editable test case.
- Updated compact, scrollable, warm-neutral overlay with clear recording state and action controls.

## Hybrid AI runtime status

This release implements the **local/offline analysis layer** and a provider-neutral boundary for future AI integration. The current suggestions are deterministic heuristics, not responses from a generative AI model. No remote AI endpoint, API key or network transmission is configured in this build.

A future approved remote provider can enrich suggestions from a privacy-filtered metadata payload only after explicit project configuration and consent. Do not embed provider secrets in the APK. Prefer an organisation-managed gateway, short-lived credentials and server-side key storage.

## Privacy and safety

- Editable text and password values are not read for inventory, AI analysis or reports.
- The local advisor receives only labels, control types, categories and basic state metadata. Password/OTP/destructive labels are excluded from its input.
- No screenshots are captured and no screen data is uploaded.
- Inventory, saved test cases and execution history remain local unless the tester explicitly shares them.
- Replay is a separate, individually approved click action. No unattended chaining, text entry, scrolling or swiping.
- AI suggestions are placeholders, not verified requirements or test results. A human tester must review them.
- Keyword-based sensitive-action filtering can miss risky actions or flag harmless ones. Never use ScanBug to operate real payment, banking, medical or other sensitive accounts.

Use only apps and environments you own or are authorized to test. The user can disable Accessibility access at any time in Android Settings.

This is a QA utility, not an accessibility tool designed to assist people with disabilities. It must not set `isAccessibilityTool=true`. Google Play distribution requires accurate Accessibility API declarations and compliance with current policy; approval is not guaranteed.

## Limitations

Only controls exposed through Android accessibility APIs can be inspected. Custom-rendered, protected, unlabeled or source-less controls may not be highlighted or captured. A visual outline appears only when usable screen bounds are exposed. Local suggestions are based on visible UI metadata and cannot infer backend rules, business requirements or hidden workflows. Expected-text checks inspect only the current visible screen and do not prove a business transaction succeeded.

## Build

1. Open `apps/android` in Android Studio with JDK 17 and Android SDK Platform 35.
2. Sync Gradle and build the debug APK.
3. Install ScanBug on a test device.
4. Read and accept the Accessibility disclosure, then enable the service in Android Settings.
5. Select an app you are authorized to test and open it.
6. Use **Explore screen** to inspect the current screen or **AI test suggestions** to generate local test placeholders.
7. Start recording, perform actions manually in the target app, and stop recording to review the captured flow.
8. Review assertions, save a test case and share reports only when needed.

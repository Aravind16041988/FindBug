# ScanBug Android 1.0.0 — AI-first mobile QA

ScanBug is a native Android QA assistant for applications the tester explicitly selects and is authorized to test. AI-assisted exploration is the primary workflow; manual recording and replay are secondary.

## Primary workflow: Run AI testing

1. Select an app and open it.
2. Tap **Run AI testing** in the ScanBug overlay.
3. ScanBug minimises to a floating AI indicator so the target app remains usable.
4. The on-device planner inspects accessible UI metadata and automatically explores a bounded set of clearly labelled, low-risk navigation controls.
5. It highlights each attempted action, records a local action log and stops when no more safe actions are available, the action limit is reached, or the target app is no longer available.
6. Tap the floating indicator to inspect progress or stop the run.
7. ScanBug displays the exploration summary and test ideas. A human tester reviews outcomes and any scenario requiring data, credentials or business-specific expected results.

## What this AI runtime does today

- Uses accessible control labels, types, package identity, visibility and enabled/clickable state to plan a bounded exploration.
- Automatically clicks only explicitly allowlisted navigation-style labels (e.g. tabs, search/filter, category, details, back/home/menu) after applying destructive-action and sensitive-flow exclusions.
- Caps an exploration run at eight click actions.
- Does not click editable, password, checkable, long-clickable or scrollable controls.
- Does not enter data, submit forms, authenticate, transact, purchase, delete, send messages, or trigger other high-impact operations.
- Produces a local action summary; the existing AI Test Advisor can generate test placeholders from the current accessible screen.

**AI honesty:** Version 1.0.0 is an offline, deterministic heuristic agent, not a connected generative AI model. It does not understand app business requirements or prove backend outcomes. The architecture is a privacy-first foundation for a future optional hybrid provider. No remote AI endpoint, API key, or network upload is configured.

## Secondary workflows

- Manual recording with a draggable floating REC chip, live action count, pause/resume and stop.
- Individual, user-approved replay clicks.
- Read-only UI inventory, visible-text assertions, local test cases, run history and HTML report sharing.

## Privacy and safety

- Editable text and password values are not read or stored.
- AI exploration uses only accessible UI metadata from the selected app.
- No screenshots are captured or uploaded.
- No cloud processing or telemetry is configured.
- Only apps the tester owns or is authorized to test should be selected.
- Label filtering is heuristic and cannot guarantee that an action is harmless. The agent therefore uses a narrow allowlist and excludes authentication, account, purchase, checkout, messaging, settings/permission and destructive flows. Test only in a controlled environment.
- The agent does not autonomously fill forms or submit data. Such scenarios require a later test-data policy and explicit test-environment safeguards.

## Limitations

Only controls exposed through Android Accessibility APIs can be inspected. Custom-rendered, protected, unlabeled or source-less controls may be missed. Some safe navigation controls may not match the allowlist and will be skipped. A click being accepted by Android does not prove the destination loaded or that business behaviour succeeded. The user must inspect the generated summary and verify outcomes.

## Build

Open `apps/android` in Android Studio with JDK 17 and Android SDK Platform 35, sync Gradle, and build the debug APK. Enable the Accessibility Service only after reading the in-app disclosure. Select a permitted target app and choose **Run AI testing**.

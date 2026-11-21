# ScanBug Android Standalone QA Agent — Product Proposal

**Status:** Proposed roadmap / not implemented  
**Product goal:** Let a user test another installed Android app from the same Android phone, without requiring a PC, ADB host, cloud runner, or paid AI API for the core workflow.

## 1. Product vision

Add an Android-native companion to ScanBug that can explore an app selected by the user, perform safe UI interactions, check expected behaviour, capture evidence, and produce a bug report locally on the device.

The product should be honest about Android platform limits: it can automate the UI exposed to Android Accessibility Services, but it cannot guarantee access to every control or inspect another app's private memory, database, source code, or protected screens.

## 2. Target user journey

1. User installs ScanBug Android and opens it.
2. User selects an installed target app from a visible, user-approved app list.
3. ScanBug explains the required Android permissions and why each is needed.
4. User enables the Accessibility Service and, for screenshot-based checks, grants MediaProjection capture permission when prompted.
5. User chooses a test mode: guided exploration, record-and-replay, or a reviewed AI-generated test plan.
6. ScanBug launches the target app and performs only the approved, non-destructive actions.
7. ScanBug records visible UI state, action results, timing, screenshots (when permitted), and detected anomalies.
8. User reviews findings and exports a local HTML/JSON report or shares it explicitly.

## 3. Proposed feature scope

### MVP — fully on-device

- Installed-app picker with package name and launchable app details.
- Explicit per-run target selection and visible start/stop controls.
- Accessibility-based UI tree inspection for exposed text, content descriptions, roles, bounds, and clickability.
- Tap, long-press, swipe, scroll, back, and text-entry actions where the target exposes accessible controls.
- Manual test recording and replay using semantic selectors, with coordinate fallback clearly marked as fragile.
- Guided exploration with limits: maximum steps, duration, screen transitions, and interaction depth.
- Assertions for visible text, element presence, enabled/disabled state, and navigation outcome.
- Screenshot capture only after Android's user-mediated MediaProjection consent; do not bypass secure-window protections.
- Local event timeline, screenshots, run history, and HTML/JSON report generation.
- Offline rules-based anomaly detection; no API key or network required for core execution.

### Later enhancements

- Optional on-device language model for test ideas, screen summaries, and bug clustering.
- Optional user-configured remote LLM provider, disabled by default and with explicit data-sharing consent.
- OCR for text visible in screenshots when accessibility metadata is missing.
- Visual comparison with user-approved baselines.
- Accessibility checks (labels, touch target size, contrast heuristics).
- API checks only when the user supplies an authorized API specification or endpoint.
- Export/import reusable test journeys between devices.

## 4. Android implementation approach

Recommended native stack:

- **Language/UI:** Kotlin + Jetpack Compose.
- **Automation:** Android AccessibilityService, using AccessibilityNodeInfo actions and gestures.
- **App discovery/launch:** PackageManager with Android package-visibility declarations kept as narrow as possible.
- **Screenshots:** MediaProjection with Android's foreground-service and consent requirements.
- **Persistence:** Room (SQLite) for projects, test steps, runs, findings, and metadata.
- **Background work:** WorkManager for report generation and cleanup; keep interactive automation in a user-visible session.
- **Local intelligence:** deterministic rules first; optional on-device model later.
- **Reports:** locally generated HTML and JSON, with explicit share/export action.

Keep this Android module separate from the existing React/Vite + Express + Playwright web workspace. Share product concepts and report schema where practical, but do not make the phone app depend on the web backend.

## 5. Platform constraints and honest product boundaries

- Android does not provide an ordinary app unrestricted control over every other app. Cross-app interaction requires explicit user-enabled Accessibility Service access and must be visible, user initiated, and easy to stop.
- Accessibility APIs expose only UI information that the target app makes available. Custom-drawn canvases, some games, embedded content, and poorly labelled controls may be difficult or impossible to inspect reliably.
- Secure windows and protected content may block screenshots. ScanBug must respect those protections.
- App-private files, databases, memory, source code, and internal logs are not generally available to a non-root third-party app.
- Reliable system-level crash/logcat diagnostics may require ADB, privileged access, or OEM/system integration. The standalone MVP should report observable symptoms, not claim complete crash-dump coverage.
- Android version, OEM battery management, permission revocation, and app lifecycle behaviour can interrupt long sessions.
- Some apps prohibit automation or contain sensitive financial, health, or personal data. The user must have authorization; the product must not bypass authentication, CAPTCHAs, device security, or app protections.
- Never execute payments, transfers, purchases, deletion, account changes, messages, or other destructive actions automatically. Treat such flows as stop-and-request-human-review boundaries.
- Do not capture passwords, OTPs, payment credentials, or other secrets. Mask sensitive fields and avoid storing typed values by default.

## 6. Safety, privacy, and consent requirements

- Run only against apps the user owns or is authorized to test.
- Show the selected target app and active automation status throughout a run.
- Provide a persistent stop control and a hard timeout/step budget.
- Require explicit approval before executing generated plans.
- Use an action allowlist; block destructive and financial actions in the MVP.
- Store evidence locally by default; no automatic upload or telemetry containing screen contents.
- Explain exactly what Accessibility and screen-capture permissions expose.
- Provide retention controls and one-tap deletion of local run data.
- Redact likely sensitive text in reports and screenshots where feasible.
- Never attempt stealth, permission bypass, or hidden background control.

## 7. Suggested architecture

    ScanBug Android (Kotlin + Compose)
      ├── App Picker / Consent & Permissions
      ├── Test Session Controller
      ├── Accessibility Automation Service
      │     ├── UI Tree Snapshotter
      │     ├── Action Executor
      │     └── Semantic Selector Resolver
      ├── Optional MediaProjection Capture
      ├── Test Planner (rules first; optional on-device AI)
      ├── Assertion & Anomaly Engine
      ├── Local Run Store (Room)
      └── Report Generator (HTML / JSON)
               |
               └── Explicit user export/share

## 8. Delivery plan

| Phase | Deliverable | Acceptance criteria |
|---|---|---|
| 0 — Feasibility | Kotlin proof of concept | User selects and launches a test app; ScanBug reads a sample accessible UI tree after explicit permission. |
| 1 — Manual automation | Tap, swipe, scroll, back, text entry | User can execute a short, reviewed journey and stop it at any time. |
| 2 — Recorder/replay | Save and replay journeys | Replay prefers semantic selectors and clearly reports unresolved/ambiguous elements. |
| 3 — Assertions/reports | Checks, timeline, local HTML/JSON | Findings include reproducible steps, expected/actual result, timestamps, and evidence. |
| 4 — Guided exploration | Bounded state exploration | Step/time limits, loop detection, safe-action policy, and human approval are enforced. |
| 5 — On-device AI | Optional natural-language planning | Works offline when a compatible local model is available; AI output is reviewed before execution. |

## 9. Initial proof-of-concept backlog

1. Create apps/android as an independent Kotlin Android application.
2. Add onboarding that explains Accessibility Service limitations and obtains user consent.
3. Implement a package picker and launch selected target app.
4. Implement a debug screen showing the current accessible node tree for a user-selected target.
5. Add a single-step tap action with visible confirmation and stop control.
6. Add swipe/back actions and a bounded action log.
7. Add a Room schema for test cases, steps, runs, and findings.
8. Add a local report with a clear distinction between confirmed failures and heuristic warnings.
9. Test on a small matrix of Android versions and OEM devices, including apps with Compose, native Views, and WebViews.

## 10. Product positioning

**ScanBug Android: test Android apps from your Android phone.**

Position the first release as a privacy-first, on-device UI exploration and regression assistant — not as a universal replacement for Appium, ADB, cloud device farms, or professional security testing. The key differentiator is a practical, standalone mobile workflow with no mandatory PC or cloud dependency.

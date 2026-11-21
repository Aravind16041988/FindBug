# ScanBug

AI-assisted autonomous QA: turn a plain-English test objective into a structured plan, review it, run approved steps in Playwright, and inspect screenshot evidence.

## Current capabilities

### Web QA MVP

- React + TypeScript + Vite workspace
- Express + TypeScript API
- Zod-validated test plans
- Optional OpenAI-compatible chat-completions provider
- Clearly labelled local fallback planner when no LLM is configured
- Explicit human approval before browser execution
- Playwright actions: navigate, click, fill, visibility/text assertions, screenshots
- Automatic access to public HTTP/HTTPS websites with DNS/IP-based SSRF protections
- In-memory run state and screenshot evidence
- Optional visible Playwright Chromium desktop via noVNC in Codespaces

### Android standalone QA proof of concept

A native Android project now lives in [apps/android](apps/android/README.md). The first device-only POC can:

- List launchable installed apps and open the selected target.
- Use a user-enabled Android Accessibility Service to inspect UI labels exposed by the target app.
- Display a visible ScanBug QA overlay only while the selected target package is active.
- Let the user tap an accessible control by entering its label in the overlay.
- Keep the latest UI text snapshot locally on the device.

This is an early interaction prototype, **not yet an autonomous AI tester**. Journey recording/replay, screenshots, guided exploration, AI planning, crash diagnostics, and downloadable reports are future work. Android accessibility cannot inspect every custom or protected UI and does not grant access to another app's private files, memory, credentials, or source code.

Build and device setup instructions: [Android POC README](apps/android/README.md). Product direction and phased plan: [Android QA agent proposal](docs/ANDROID_STANDALONE_QA_AGENT.md).

## Requirements and web setup

Node.js 22+ and npm 10+. For the separate visible Chromium window in Codespaces, use the included Dev Container configuration and rebuild the Codespace container.

    cp .env.example .env
    npm install
    npx playwright install chromium
    npm run dev

Web UI: http://localhost:5173  
API health: http://localhost:4000/api/health  
Remote Chromium desktop (Codespaces): open forwarded port 6080 and launch noVNC.

For local development only, set SCANBUG_ALLOW_LOCAL=true to permit localhost targets. Never enable this override on a publicly accessible or untrusted runner.

## Optional LLM provider

Set LLM_BASE_URL, LLM_API_KEY, and LLM_MODEL in the root .env. The server sends the goal and target URL to that configured provider and validates its JSON output. API keys remain server-side. Do not submit secrets, customer data, or production credentials in prompts. Without these settings, the UI labels the fallback as local-fallback (not an LLM).

## Execution safety

- Test only systems you own or are authorized to test.
- Review generated plans before approving execution.
- The web runner blocks localhost, private/reserved IP ranges, internal hostnames and non-HTTP(S) protocols by default.
- The Android POC requires explicit Accessibility Service enablement, scopes its overlay to the selected target package, and does not automate destructive or financial actions.

## Repository structure

    apps/web       React UI
    apps/api       Express API, planner adapter, Playwright runner
    apps/android   Native Android UI-inspection/tap proof of concept
    packages/shared Zod schemas and shared types

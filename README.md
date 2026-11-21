# ScanBug

AI-assisted autonomous QA: turn a plain-English test objective into a structured plan, review it, run approved steps in Playwright, and inspect screenshot evidence.

## MVP capabilities

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

Not yet implemented: database persistence, authentication/multi-tenancy, encrypted credential vault, robust network capture, visual AI comparison, selector self-healing, API test authoring, distributed workers, or production deployment hardening. These are future milestones, not implied by this MVP.

## Proposed direction: standalone Android QA agent

ScanBug is also exploring a native Android companion that can test another installed Android app directly from the same phone, without a mandatory PC, ADB host, cloud runner, or paid AI API for its core workflow.

The proposed Android app would use a user-enabled Accessibility Service for visible UI inspection and interaction, optional user-approved screen capture, local test execution, and on-device reports. It is **not implemented yet** and will have Android platform limitations: apps can hide UI from accessibility, secure screens may block screenshots, and third-party apps cannot access another app's private data or unrestricted system logs.

See [Android Standalone QA Agent proposal](docs/ANDROID_STANDALONE_QA_AGENT.md) for the product vision, architecture, safety boundaries, and phased backlog. The existing React/Vite + Express + Playwright application remains the current implemented MVP.

## Requirements and setup

Node.js 22+ and npm 10+. For the separate visible Chromium window in Codespaces, use the included Dev Container configuration and rebuild the Codespace container.

    cp .env.example .env
    npm install
    npx playwright install chromium

No domain allowlist is needed for public websites. In Codespaces, the noVNC remote desktop is available on port 6080; keep this port private. ScanBug validates each navigation and browser request, rejecting localhost, private/reserved IP ranges, internal hostnames, and non-HTTP(S) protocols.

    npm run dev

Web UI: http://localhost:5173  
API health: http://localhost:4000/api/health  
Remote Chromium desktop (Codespaces): open forwarded port 6080 and launch noVNC.

For local development only, set SCANBUG_ALLOW_LOCAL=true to permit localhost targets. Never enable this override on a publicly accessible or untrusted runner.

## Optional LLM provider

Set LLM_BASE_URL, LLM_API_KEY, and LLM_MODEL in the root .env. The server sends the goal and target URL to that configured provider and validates its JSON output. API keys remain server-side. Do not submit secrets, customer data, or production credentials in prompts. Without these settings, the UI labels the fallback as local-fallback (not an LLM).

## Execution safety

- Test only systems you own or are authorized to test.
- Review the generated plan before approving execution.
- Public HTTP/HTTPS destinations are allowed without per-domain configuration.
- Every browser request, including page resources and redirects, is checked against DNS-resolved IP addresses.
- Localhost, private/reserved IP ranges, internal hostnames and non-HTTP(S) protocols are blocked. Localhost can be enabled only for isolated local development with SCANBUG_ALLOW_LOCAL=true.
- This MVP has no password-entry action and no destructive/payment/account-management actions.
- Run records are in memory and disappear when the API restarts. Screenshots are written under apps/api/storage.

## Structure

    apps/web       React UI
    apps/api       Express API, planner adapter, Playwright runner
    packages/shared Zod schemas and shared types

## Roadmap

1. PostgreSQL persistence, project/workspace model and authentication.
2. Encrypted secrets vault and approved authentication workflows.
3. Console/network evidence correlation and downloadable HTML reports.
4. API testing, accessibility checks, visual regression and AI-assisted RCA.
5. Selector healing with confidence scoring and explicit approval.
6. Isolated queue-backed workers, Docker deployment, quotas and audit logging.
7. **Proposed:** native Android standalone QA agent — see [proposal](docs/ANDROID_STANDALONE_QA_AGENT.md).

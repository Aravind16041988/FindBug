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
- Explicit target-host allowlist; localhost blocked unless opted into for local development
- In-memory run state and screenshot evidence

Not yet implemented: database persistence, authentication/multi-tenancy, encrypted credential vault, robust network capture, visual AI comparison, selector self-healing, API test authoring, distributed workers, or production deployment hardening. These are future milestones, not implied by this MVP.

## Requirements and setup

Node.js 22+ and npm 10+.

```bash
cp .env.example .env
npm install
npx playwright install chromium
```

Edit `.env` and set `SCANBUG_ALLOWED_HOSTS` to the exact hostname(s) of test systems you own or are authorized to assess. For example: `SCANBUG_ALLOWED_HOSTS=staging.example.com`.

```bash
npm run dev
```

Web UI: http://localhost:5173  
API health: http://localhost:4000/api/health

For local test applications only, add `localhost,127.0.0.1` to `SCANBUG_ALLOWED_HOSTS` and set `SCANBUG_ALLOW_LOCAL=true`. Never enable this override on an exposed/untrusted runner.

## Optional LLM provider

Set `LLM_BASE_URL`, `LLM_API_KEY`, and `LLM_MODEL` in the root `.env`. The server sends the goal and target URL to that configured provider and validates its JSON output. API keys remain server-side. Do not submit secrets, customer data, or production credentials in prompts. Without these settings, the UI labels the fallback as `local-fallback (not an LLM)`.

## Execution safety

- Test only systems you own or are authorized to test.
- Review the generated plan before approving execution.
- Runner rejects hosts outside `SCANBUG_ALLOWED_HOSTS`.
- Localhost is blocked unless the explicit local-development override is enabled.
- This MVP has no password-entry action and no destructive/payment/account-management actions.
- Run records are in memory and disappear when the API restarts. Screenshots are written under `apps/api/storage`.

## Structure

```
apps/web       React UI
apps/api       Express API, planner adapter, Playwright runner
packages/shared Zod schemas and shared types
```

## Roadmap

1. PostgreSQL persistence, project/workspace model and authentication.
2. Encrypted secrets vault and approved authentication workflows.
3. Console/network evidence correlation and downloadable HTML reports.
4. API testing, accessibility checks, visual regression and AI-assisted RCA.
5. Selector healing with confidence scoring and explicit approval.
6. Isolated queue-backed workers, Docker deployment, quotas and audit logging.

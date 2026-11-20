# ScanBug

AI-assisted autonomous QA platform. Describe a test goal in plain English, review a structured test plan, execute approved browser steps with Playwright, and inspect evidence-backed results.

## MVP status
This repository is being initialized. The first release focuses on reviewed natural-language plans, safe browser execution, screenshots, and clear run reports. Advanced self-healing, visual AI, API testing, and multi-worker orchestration are planned extensions, not claimed as complete.

## Planned stack
- React, TypeScript, Vite
- Node.js, Express, TypeScript
- Playwright
- Provider-agnostic LLM adapter
- Zod validation

## Safety
Only test systems you own or are authorized to assess. Review every AI-generated plan before execution. Do not place credentials in prompts, URLs, source control, or logs. Use an explicit target-host allowlist.

## Local development
See the project setup instructions as the application files are added.
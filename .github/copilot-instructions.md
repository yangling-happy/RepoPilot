When performing a code review for this repository:

- Prefer concise, actionable comments. Use Simplified Chinese for natural-language feedback when the surrounding discussion is in Chinese; otherwise English is fine.
- Flag security issues (auth, secrets, injection, unsafe deserialization) and call them out explicitly.
- For `apps/web` (React + Vite + i18next): flag user-visible hardcoded strings that should use `t(...)` / translation keys; respect existing Tailwind / Ant Design patterns.
- For `apps/terminal` (TypeScript): watch for WebSocket lifecycle leaks, missing cleanup, and unsafe `any` creeping into public APIs.
- For `backend` (Spring Boot / Java 17): prefer consistent exception handling, validate external input, avoid logging secrets, and keep module boundaries (common / business / terminal / gateway) clear.

Do not nitpick formatting that is already enforced by CI (ESLint / Prettier / Maven).

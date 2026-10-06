# MedRep PWA

Installable web app for medical sales reps: **visits, visit plans and settlements**, with searchable **healthcare professional** and **medical sales rep** pickers. It talks only to the API gateway. Built with React 18, TypeScript, Vite, TanStack Query, zod and vite-plugin-pwa (Workbox).

Feature parity with [`mobile-android/`](../mobile-android/); the backend quirks listed there apply here too.

## Run it

```bash
cp .env.example .env          # defaults are fine for local dev
npm ci
npm run dev                   # http://localhost:5174 (proxies /api and /auth to GATEWAY_URL)
```

Start the backend first (gateway on `localhost:8080`, e.g. `../start-all-services.sh`).

| Script | What it does |
|---|---|
| `npm run dev` | Dev server with the gateway proxy |
| `npm test` | Unit and component tests (Vitest + Testing Library + MSW) |
| `npm run typecheck` | `tsc` for the app and the Vite config |
| `npm run build` | Type-check, then production build to `dist/` (incl. `sw.js` and the manifest) |
| `npm run preview` | Serves `dist/` on :4174 with the same proxy |
| `npm run smoke` | Builds, then drives the real app in headless Chromium with the **production security headers** and a mock gateway (run `npx playwright install chromium` once) |
| `npm run audit:prod` | `npm audit` for shipped dependencies |

## Architecture (MVVM)

| Role | Where | Rule |
|---|---|---|
| **View** | `features/*/…Views.tsx`, `core/ui/*` | Renders ViewModel state and forwards user intents. No fetch, no business rules. |
| **ViewModel** | `features/*/…ViewModels.ts` (`useVisitListViewModel`, …) | Hooks over TanStack Query. Expose display-ready state (`rows`, `error`, `saving`) and commands (`submit`, `loadMore`, `upload`). |
| **Model** | `features/*/…Repository.ts`, `core/http.ts` | Gateway calls, zod validation of every response, DTO mapping. |

```
src/
├── app/            App, routes (session gate), AppShell (bottom nav), query client, SW update prompt
├── core/
│   ├── http.ts     the only fetch: in-memory bearer token, error mapping, schema validation
│   ├── session.ts  login/logout/idle timeout/401 → wipes token + query cache
│   ├── env.ts      API base URL (https enforced in production builds)
│   ├── format.ts   zone-less LocalDate/LocalDateTime handling, money display
│   ├── files.ts    upload rules (extensions, 10 MB)
│   ├── trustedTypes.ts  default Trusted Types policy
│   └── ui/         Picker (ARIA combobox), PagedList, FormPage, Field, Modal, …
├── features/
│   ├── auth/       LoginView + view model + repository
│   ├── hcp/  msr/  directory repositories (session cache), pickers, id → name
│   ├── visit/      visits + visit plans
│   └── settlement/ settlements + invoices (multipart upload, DELETE with body)
└── test/           MSW server, render helper
```

### HCP / MSR pickers

The backend name search is exact-match, and an empty filter returns everyone unpaginated. Each directory is therefore loaded once per session and filtered on the device: by name, email or specialty, with specialty chips for HCPs. The same data turns ids into names in lists. For directories in the thousands, add a partial-match, paginated search endpoint.

## Security (OWASP Top 10 2021)

| Risk | Control | Where |
|---|---|---|
| **A07 Auth failures** | JWT kept **in memory only**, never in local/session storage. Session ends on sign-out, gateway 401, or 15 min idle (`VITE_IDLE_TIMEOUT_MINUTES`); ending it wipes the token and the query cache. Generic login error. Password cleared after each attempt. `autocomplete` hints for password managers. | `core/http.ts`, `core/session.ts`, `features/auth` |
| **A03 Injection / XSS** | React escaping only (no `dangerouslySetInnerHTML`). Strict **CSP** without `unsafe-inline`. **Trusted Types** enforced, with a default policy that only allows same-origin script URLs. Path segments `encodeURIComponent`-ed. | `nginx/security-headers.conf`, `core/trustedTypes.ts` |
| **A02 Data exposure** | HTTPS required in production builds. Service worker precaches the app shell only; `/api` and `/auth` are `NetworkOnly`, so no personal data goes to Cache Storage. `fetch(..., { cache: "no-store" })`, plus gateway `Cache-Control: no-store`. No console output in production builds; no source maps. | `vite.config.ts`, `core/env.ts`, gateway `application.yml` |
| **A05 Misconfiguration** | Same-origin deployment (nginx reverse proxy), so no CORS to get wrong. HSTS, `nosniff`, `frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, COOP/CORP. Non-root nginx image, `server_tokens off`. | `nginx/`, `Dockerfile` |
| **A01 Access control** | The UI only hides; the backend authorizes. No tokens or personal data in URLs. | — |
| **A04 Insecure design (uploads)** | Extension/size/empty checks before upload (the server re-checks). `accept` filters on file inputs. Upload body capped at 11 MB in nginx. | `core/files.ts` |
| **A08 Integrity** | Every response validated with zod before use. Lockfile + `npm ci`. Everything bundled (no runtime CDN). | repositories |
| **A06 Components** | `npm run audit:prod` reports **0** vulnerabilities in shipped dependencies. | — |
| **A09 Logging** | Login success/failure is audited server side; the client logs nothing sensitive. | — |
| **CSRF** | Not applicable: bearer header, `credentials: "omit"`, no cookies. | `core/http.ts` |

### Trade-off: memory-only token

Reloading the page signs the user out. That's the price of keeping the token away from any injected script. The production-grade fix is a small **BFF** that holds the JWT server side and gives the browser a `Secure; HttpOnly; SameSite=Strict` session cookie, plus CSRF protection for writes.

### Dev-only advisories

`npm audit` (including dev dependencies) still reports issues in Tailwind CSS 3's file-watcher chain (`braces`, `chokidar`, `micromatch`) and in Vitest 3's mocker. These run only at build/test time and are not shipped. Clearing them means migrating to Tailwind 4 and Vitest 5.

## Deploy

```bash
docker build -t medrep-pwa .
docker run -p 8081:8080 -e GATEWAY_URL=http://api-gateway:8080 --network <compose-network> medrep-pwa
```

- **nginx config:** nginx serves `dist/` and proxies `/api` and `/auth` to `GATEWAY_URL`. `GATEWAY_URL` must be scheme + host + port, with no path.
- **Origin header:** nginx drops the browser's `Origin` when proxying. The gateway would otherwise treat the same-origin call as cross-origin. This is safe because auth is a bearer token.
- **TLS:** terminate TLS in front of the container. The CSP includes `upgrade-insecure-requests` and HSTS.
- **Separate API origin:** build with `--build-arg VITE_API_BASE_URL=https://api.example.com/`, run with `-e API_ORIGIN=https://api.example.com` (added to CSP `connect-src`), and add the PWA origin to the gateway CORS allow-list (`CORS_ALLOWED_ORIGINS` in the prod profile).

## Offline

The app shell loads offline and shows an offline banner. API data is intentionally **not** cached. Offline capture of visits would need an IndexedDB draft queue with Background Sync, cleared on sign-out.

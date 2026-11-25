# Back-office web app

Next.js 16 (App Router) app for orders, settlements, HCPs, sales reps, the product catalog and Quality decisions. It is a **backend-for-frontend (BFF)** with **hybrid rendering**: the browser talks only to this app, and this app talks only to the API gateway.

```
Browser ──HttpOnly cookie──▶ backoffice-ui (BFF) ──user's JWT──▶ API gateway ──▶ services
```

Field work (visits, plans, placing orders) stays in [`pwa/`](../pwa/) and [`mobile-android/`](../mobile-android/).

## Run it

Needs Node.js 22 or later.

```bash
cp .env.example .env          # fill in SESSION_SECRET and the catalog account
npm ci
npm run dev                   # http://localhost:5175
```

Start the backend first (gateway on `localhost:8080`). With compose: `docker compose up -d backoffice-ui`, then http://localhost:8083.

| Script | What it does |
|---|---|
| `npm run dev` / `build` / `start` | Development server / production build / production server (:5175) |
| `npm test` | Unit tests (Node's test runner) |
| `npm run smoke` | Builds, then drives the production server in headless Chromium against a mock gateway (run `npx playwright install --with-deps chromium` once) |
| `npm run typecheck`, `npm run audit:prod` | Types; advisories in shipped dependencies |

## Rendering

| Mode | Routes | Why |
|---|---|---|
| **SSG** | `/help` | Same for everyone, no API calls |
| **ISR** (1 h + on demand) | `/catalog/[page]`, `/catalog/product/[id]` | Product master data: shared, rarely changes, not personal |
| **SSR** | Everything else, including sign-in | Personal or per-user data, or must be current (lots, status) |
| **SSR + streaming** | Dashboard cards, audit trails, recall recipients | Slow parts follow in `<Suspense>` |
| **CSR islands** | Price and stock on a catalog product, live order status | Change fast; read with the user's own JWT |

Rules the code enforces:

- **Only master data can be cached.** `server/cacheable.ts` lists the fields; zod drops the rest, so price, stock and status can't reach the cache. ISR pages set `dynamic = "error"`, so reading cookies there fails.
- **ISR is a server-side cache only.** The proxy checks the session first and sends `private, no-store`, so no CDN or browser serves a signed-in page by itself.
- **The cache is filled by a `ROLE_USER` service account**, never by whoever came first. Every other call uses the signed-in user's JWT.
- **Three root layouts, two CSPs.** A nonce changes per response, so prerendered pages can't have one. Moving between the groups is a full page load, so the weaker policy never governs a page with personal data.
- **No `loading.tsx`.** It would send `200` before a page knows it is a redirect, 403 or 404.

## Communication

| Need | How |
|---|---|
| Page data | Server components → `server/session.ts` `asUser()` → gateway; responses validated with zod |
| Writes | Server Actions: session and role check, zod validation, gateway call, then `revalidatePath` / `updateTag` |
| Live order status | SSE `GET /bff/orders/{id}/events`. The backend has no push API, so the BFF polls with back-off (2 to 20 s, 5 min at most, 5 streams per user) |
| Live price and stock | `GET /bff/products/{id}/live` |
| Changes made outside this app | `POST /bff/revalidate` (below), else up to 1 h |

### On-demand revalidation

Product edits made in this app refresh the catalog at once. For changes made elsewhere, for example from a consumer of `catalog.events` (not built yet):

```bash
BODY='{"type":"product"}'      # or {"type":"specialties"}
TS=$(date +%s)
SIG=$(printf '%s.%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$REVALIDATE_SECRET" -hex | sed 's/.* //')
curl -X POST "$BACKOFFICE_URL/bff/revalidate" -H "x-timestamp: $TS" -H "x-signature: $SIG" -d "$BODY"
```

The ISR cache is in each instance's memory. With several instances, call every one, or configure a shared Next.js cache handler.

## Security

| Risk | Control |
|---|---|
| Token theft | JWT encrypted (A256GCM) in a `__Host-`, `HttpOnly`, `Secure`, `SameSite=Strict` cookie; never sent to scripts |
| Session end | Sign-out, JWT expiry, gateway 401, or 15 min idle (`IDLE_TIMEOUT_MINUTES`); background calls don't extend it |
| CSRF | `SameSite=Strict` plus Next's Origin check on Server Actions |
| XSS | Nonce CSP with `strict-dynamic`; React escaping only |
| Access control | Proxy, then again in each page, action and route handler; the backend decides. ISR pages rely on the proxy and hold no sensitive data |
| Open redirect | `?next=` accepts only paths on this site |
| Rate-limit evasion | `scripts/client-ip.mjs` appends the peer address; only the trusted end of `X-Forwarded-For` is used and passed on |
| Error leaks | Backend 5xx text is never shown |

Sign-out deletes the cookie, but the backend can't revoke a JWT: a stolen cookie works until it is idle or expires.

## Configuration

See [`.env.example`](.env.example). The server exits at startup if `GATEWAY_URL`, `SESSION_SECRET` or the catalog account is missing. All instances must share `SESSION_SECRET`. Behind a TLS proxy set `TRUSTED_PROXY_HOPS=1`.

## Known limits

- **Gateway rate limit:** 20 requests per minute per client address by default. A page here makes 1 to 3 calls, so a busy user can reach it, and people behind one address share it. Limit by user in the gateway, or raise `gateway.ratelimit.*`.
- **Orders, HCPs, sales reps and products:** the backend's unfiltered lists aren't paginated, so the BFF reads the full list and sends the browser one page.
- **Order approval** is the AI Agent Service's (`ROLE_AGENT`), so there is no approve button.
- **Settlements with invoices** can't be edited: the backend's update would drop the invoice files.
- **Safari** refuses `Secure` cookies on plain-http localhost; use another browser or TLS.

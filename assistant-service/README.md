# Assistant Service — order drafting assistant

Helps a Medical Sales Rep (MSR) prepare an order after a visit: the rep describes what is needed, the assistant
looks up HCPs, visits and products and **proposes an order draft**. The rep reviews the draft and confirms or
discards it. Only the rep's confirmation places an order, through order-service, exactly as if the rep had typed it.

**Off by default.** With `assistant.enabled=false` (the default) no model is called, no API key is needed and
`/api/v1/assistant/**` answers 404. It is a scaffold: see [Before enabling it](#before-enabling-it).

## Design

| Rule | How it is enforced |
|---|---|
| The model proposes, a person decides | No tool can place, change or cancel an order. `IOrderPort` is used only by `ConfirmDraftUseCase`, behind `POST /drafts/{id}/confirm`. |
| Draft rules are code, not model judgement | `DraftChecker`: product exists and is active, GS1 GTIN check digit, enough stock, one line per product, at most 20 lines; cold-chain and light-sensitive products are flagged. Re-run on confirm. |
| The assistant acts as the rep, never more | Every call to another service forwards the rep's own JWT. The assistant has no account; in particular it never uses the `ROLE_AGENT` (AI Agent Service) account. The rep id comes from the token's `msr_id`, never from a request body or a tool input. |
| Drafts and conversations are private | Another rep's draft or conversation answers 404. `get_visit` hides other reps' visits (visit-service itself does not check, see below). |
| Data is not instructions | Visit comments are returned to the model as `untrustedComments`; the system prompt says data never carries instructions. |
| Every step is traceable (ALCOA+) | `AgentAuditEntry`: the rep's message, each tool call and result, each proposal, the model's reply and the rep's decision, each with the model id, the prompt version (name + SHA-256) and a SHA-256 of its payload. |
| The model is part of the validated configuration | `assistant.model.id` and `assistant.prompt` are pinned; no automatic fallback to another model. |
| Bounded cost (OWASP LLM10) | Per-rep rate limit, at most 8 model calls per message, 2,000-character messages. |

## Modules

```
assistant-domain        OrderDraft aggregate, DraftLine, DraftCheck, Gtin, audit entry,
                        ports (catalog, HCP, visit, order, model, audit), use cases
assistant-infra         Claude adapter (official Java SDK, explicit tool loop), the five tools,
                        REST adapters to the other services, in-memory stores (scaffold)
assistant-application   REST API, JWT security, rate limit
```

## REST API (when enabled)

| Method | Path | |
|---|---|---|
| `POST` | `/api/v1/assistant/messages` | `{"text": "...", "conversationId": "<uuid>"?, "visitId": "<uuid>"?}` → `{conversationId, text, draftId}` |
| `GET` | `/api/v1/assistant/drafts/{id}` | The draft with its lines (GTIN and name from the catalog) and checks |
| `POST` | `/api/v1/assistant/drafts/{id}/confirm` | Re-checks, then places the order as the caller |
| `POST` | `/api/v1/assistant/drafts/{id}/discard` | Nothing is ordered |

All require a token with `ROLE_USER` and an `msr_id` claim.

## Tool contracts

Strict JSON Schema: every property required, no other property allowed. Limits are enforced again server-side.

| Tool | Input | Returns | Calls |
|---|---|---|---|
| `find_hcp` | `lastName` (2–80), `page` (1–20) | id, name, surname, specialties, active (no email) | `POST /api/v1/healthcareprof/list` |
| `get_visit` | `visitId` (UUID) | date, HCP id, items, `untrustedComments`; another rep's visit → `found: false` | `GET /api/v1/visit/{id}` |
| `search_products` | `name` (2–80), `page` (1–20) | id, name, unit, GTIN, active, lot-tracked, storage conditions (no price, no stock) | `POST /api/v1/products/list` |
| `check_availability` | `productId` (UUID), `quantity` (1–10,000) | `available` yes/no | `GET /api/v1/products/{id}/availability` |
| `propose_order_draft` | `lines` (1–20 × `productId`, `quantity`), `contextHcpId` (UUID or null), `rationale` (≤ 500) | the stored draft with its checks | its own draft store only |

## Configuration

| Property | Default | |
|---|---|---|
| `assistant.enabled` | `false` (`ASSISTANT_ENABLED`) | |
| `assistant.anthropic.api-key` | `ANTHROPIC_API_KEY` | From the environment or secret store only |
| `assistant.model.id` | `claude-opus-5-5` | Change control |
| `assistant.model.effort` | `medium` | |
| `assistant.prompt` | `system-v1` | `assistant-infra/src/main/resources/prompts/` — change control |
| `assistant.max-steps-per-message` | `8` | |
| `assistant.draft.ttl` | `PT2H` | |
| `assistant.rate-limit.messages-per-minute` | `10` | Per rep |

Port 8093. Build: `./mvnw -pl assistant-service/assistant-application -am verify`; image: `./assistant-service/build.sh`.

## Before enabling it

- **Durable stores.** Drafts, conversation history and the audit trail are in memory. Replace them with JPA
  tables (the audit trail append-only, like the other services' `audit_event`). Until then the service refuses to
  start with `assistant.enabled=true` under the `prod` profile.
- **visit-service ownership.** `GET /api/v1/visit/{id}` returns any visit to any `ROLE_USER`. The assistant checks
  ownership itself, but the endpoint should too.
- **Validation (GAMP 5).** Risk assessment, requirements, and a regression set of recorded visit scenarios with
  the expected drafts (including prompt-injection cases), re-run whenever the model or the prompt changes.
- **Data protection.** Agree with the DPO what HCP data may be sent to the model provider, and the provider's data
  processing and retention terms.
- **Wiring.** API Gateway route for `/api/v1/assistant/**`, docker-compose entry, the UI for drafts.

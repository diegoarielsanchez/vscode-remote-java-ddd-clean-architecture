import assert from "node:assert/strict";
import { beforeEach, describe, test } from "node:test";
import { SignJWT } from "jose";
import { CatalogProductSchema } from "./cacheable.ts";
import { clientIp } from "./client-ip.ts";
import { contentSecurityPolicy } from "./csp.ts";
import { emptyOnNotFound, isNotFound, toGatewayError } from "./gateway-error.ts";
import { BACKOFF_MS, followOrder } from "./order-stream.ts";
import { isPublic, routeKind, safeNext } from "./routes.ts";
import { sign, verify } from "./signature.ts";
import { cookieName, cookieOptions, expiryOf, nowSeconds, openSession, sealSession } from "./token.ts";
import { uploadProblem } from "../features/settlements/upload.ts";

beforeEach(() => {
  process.env.SESSION_SECRET = "a".repeat(32);
  process.env.IDLE_TIMEOUT_MINUTES = "15";
  process.env.COOKIE_SECURE = "true";
});
const session = (over = {}) => ({ token: "backend.jwt.value", username: "ana", roles: ["ROLE_USER"], expiresAt: nowSeconds() + 3600, seenAt: nowSeconds(), ...over });

describe("session cookie", () => {
  test("round-trips and doesn't reveal the token", async () => {
    const cookie = await sealSession(session());
    assert.ok(!cookie.includes("backend"));
    assert.equal((await openSession(cookie))?.token, "backend.jwt.value");
  });
  test("rejects tampering, another secret, garbage and absence", async () => {
    const cookie = await sealSession(session());
    const parts = cookie.split(".");
    parts[3] = (parts[3]![0] === "A" ? "B" : "A") + parts[3]!.slice(1);
    assert.equal(await openSession(parts.join(".")), null);
    assert.equal(await openSession("x"), null);
    assert.equal(await openSession(undefined), null);
    process.env.SESSION_SECRET = "b".repeat(32);
    assert.equal(await openSession(cookie), null);
  });
  test("ends at the JWT's expiry and after the idle time", async () => {
    assert.equal(await openSession(await sealSession(session({ expiresAt: nowSeconds() - 1 }))), null);
    assert.equal(await openSession(await sealSession(session({ seenAt: nowSeconds() - 15 * 60 }))), null);
    assert.ok(await openSession(await sealSession(session({ seenAt: nowSeconds() - 14 * 60 }))));
  });
  test("is HttpOnly, Secure, Strict and host-bound", () => {
    assert.equal(cookieName(), "__Host-bo_session");
    const o = cookieOptions(session());
    assert.deepEqual([o.httpOnly, o.secure, o.sameSite, o.path], [true, true, "strict", "/"]);
    assert.ok(o.maxAge <= 900);
  });
  test("refuses a short secret instead of treating everyone as signed out", async () => {
    const cookie = await sealSession(session());
    process.env.SESSION_SECRET = "short";
    await assert.rejects(openSession(cookie), /SESSION_SECRET/);
  });
  test("reads the backend token's expiry", async () => {
    const jwt = await new SignJWT({}).setProtectedHeader({ alg: "HS256" }).setExpirationTime(1234).sign(new Uint8Array(32));
    assert.equal(expiryOf(jwt), 1234);
  });
});

test("the shared cache keeps master data only", () => {
  const cached = CatalogProductSchema.parse({
    id: "1", name: "Amoxicillin", unit: "box", lotTracked: true, price: 12.5, stock: 7, active: true, costPrice: 1,
    storageCondition: { description: "2.0 to 8.0 °C", coldChain: true, internalNote: "x" },
  });
  assert.deepEqual(Object.keys(cached).sort(), ["id", "lotTracked", "name", "storageCondition", "unit"]);
  assert.ok(!JSON.stringify(cached).includes("internalNote"));
});

describe("content security policy", () => {
  test("dynamic pages: nonce, no unsafe-inline", () => {
    const csp = contentSecurityPolicy({ nonce: "abc", dev: false, secure: true });
    assert.match(csp, /script-src 'self' 'nonce-abc' 'strict-dynamic'/);
    assert.doesNotMatch(csp, /unsafe/);
    assert.match(csp, /upgrade-insecure-requests/);
  });
  test("prerendered pages never allow eval or foreign origins", () => {
    const csp = contentSecurityPolicy({ nonce: null, dev: false, secure: false });
    assert.doesNotMatch(csp, /unsafe-eval|https?:|upgrade/);
  });
});

test("routes are grouped by rendering mode; only these are public", () => {
  assert.deepEqual(["/help", "/catalog/1", "/catalog/product/x", "/", "/orders/1", "/login", "/catalogue"].map(routeKind),
    ["static", "isr", "isr", "dynamic", "dynamic", "dynamic", "dynamic"]);
  assert.deepEqual(["/login", "/help", "/catalog/1", "/orders", "/bff/session", "/help/x"].map(isPublic), [true, true, false, false, false, false]);
});

test("?next= stays on this site", () => {
  assert.equal(safeNext("/orders?page=2"), "/orders?page=2");
  for (const bad of ["//evil.com", "/\\evil.com", "https://evil.com", "javascript:alert(1)", "/a\\b", "/\n/evil.com", "/login", "/bff/expired", "", null]) {
    assert.equal(safeNext(bad), "/", String(bad));
  }
});

test("client address comes from the trusted end of X-Forwarded-For", () => {
  assert.equal(clientIp("6.6.6.6, 10.0.0.9", 0, true), "10.0.0.9");
  assert.equal(clientIp("6.6.6.6, 203.0.113.7, 10.0.0.2", 1, true), "203.0.113.7");
  assert.equal(clientIp("10.0.0.2", 1, true), null);
  assert.equal(clientIp("6.6.6.6", 0, false), null);
});

test("gateway errors: known shapes, no server detail", () => {
  assert.equal(toGatewayError(400, { statusCode: 400, message: "Lot is expired" }).message, "Lot is expired");
  assert.deepEqual(toGatewayError(400, { name: "must not be blank" }).fields, { name: "must not be blank" });
  assert.doesNotMatch(toGatewayError(500, { message: "NullPointerException at com.das" }).message, /Null/);
  assert.equal(toGatewayError(429, null).digest, "GATEWAY_429");
  assert.ok(isNotFound(toGatewayError(400, { message: "No orders found." })));
  assert.deepEqual(emptyOnNotFound(toGatewayError(400, { message: "Product not found." })), []);
  assert.throws(() => emptyOnNotFound(toGatewayError(400, { message: "Invalid page" })));
});

describe("revalidation signature", () => {
  const body = '{"type":"product"}';
  test("accepts a fresh call", () => assert.ok(verify("s", "1000", sign("s", "1000", body), body, 1100)));
  test("refuses replay, alteration, wrong secret and missing headers", () => {
    const good = sign("s", "1000", body);
    assert.ok(!verify("s", "1000", good, body, 1301));
    assert.ok(!verify("s", "1000", good, body + " ", 1000));
    assert.ok(!verify("s", "1001", good, body, 1000));
    assert.ok(!verify("t", "1000", good, body, 1000));
    assert.ok(!verify("s", null, good, body, 1000));
    assert.ok(!verify("s", "1000", null, body, 1000));
  });
});

describe("following an order", () => {
  async function run(statuses: (string | Error)[], clock = () => 0) {
    const waits: number[] = [];
    const events: string[] = [];
    let i = 0;
    const next = async () => { const s = statuses[Math.min(i++, statuses.length - 1)]!; if (s instanceof Error) throw s; return s; };
    for await (const e of followOrder(next, async (ms) => { waits.push(ms); }, clock)) events.push(e.event === "status" ? e.status : `end:${e.reason}`);
    return { events, waits };
  }
  test("reports changes once and stops when settled; back-off restarts after a change", async () => {
    const { events, waits } = await run(["AWAITING_STOCK", "AWAITING_STOCK", "PENDING_APPROVAL", "APPROVED"]);
    assert.deepEqual(events, ["AWAITING_STOCK", "PENDING_APPROVAL", "APPROVED", "end:settled"]);
    assert.deepEqual(waits, [2000, 3000, 2000]);
  });
  test("stops on errors and after five minutes", async () => {
    assert.deepEqual((await run([new Error("401")])).events, ["end:error"]);
    let t = 0;
    const { events, waits } = await run(["AWAITING_STOCK"], () => (t += 30_000));
    assert.equal(events.at(-1), "end:timeout");
    assert.ok(waits.length < 15);
  });
  test("stays well inside the gateway's 20 calls per minute", () => {
    let elapsed = 0, calls = 1;
    for (let i = 0; elapsed < 60_000; i++) { elapsed += BACKOFF_MS[Math.min(i, BACKOFF_MS.length - 1)]!; calls++; }
    assert.ok(calls <= 10, `${calls} calls in the first minute`);
  });
});

test("uploads: extension, size and emptiness", () => {
  assert.equal(uploadProblem({ name: "a.PDF", size: 1 }), null);
  assert.ok(uploadProblem({ name: "a.exe", size: 1 }));
  assert.ok(uploadProblem({ name: "pdf", size: 1 }));
  assert.ok(uploadProblem({ name: "a.pdf", size: 0 }));
  assert.ok(uploadProblem({ name: "a.pdf", size: 10 * 1024 * 1024 + 1 }));
});

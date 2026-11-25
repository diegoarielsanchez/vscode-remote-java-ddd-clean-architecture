// Drives the production build (standalone server, real proxy and headers) in headless Chromium.
// npx playwright install chromium   (once)
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createHmac } from "node:crypto";
import { writeFileSync } from "node:fs";
import { chromium } from "playwright";
import { ID, PASSWORD, startMockGateway } from "./mock-gateway.mjs";

const PORT = 5199, GATEWAY = 5198, BASE = `http://localhost:${PORT}`, SECRET = "revalidate-secret";
const gateway = await startMockGateway(GATEWAY);
const server = spawn(process.execPath, ["--import", "./scripts/client-ip.mjs", ".next/standalone/server.js"], {
  env: { ...process.env, PORT: String(PORT), HOSTNAME: "127.0.0.1", NODE_ENV: "production", GATEWAY_URL: `http://127.0.0.1:${GATEWAY}`, SESSION_SECRET: "e2e".repeat(16),
    CATALOG_SERVICE_USERNAME: "catalog", CATALOG_SERVICE_PASSWORD: PASSWORD, REVALIDATE_SECRET: SECRET },
  stdio: ["ignore", "pipe", "pipe"],
});
let log = "";
for (const s of [server.stdout, server.stderr]) s.on("data", (d) => { log += d; });

let passed = 0, failed = 0, group = "";
const results = [];
async function check(name, fn) {
  const start = Date.now();
  try { await fn(); passed++; console.log(`  ok   ${name}`); results.push({ group, name, ms: Date.now() - start }); }
  catch (e) {
    failed++;
    const message = String(e.message).split("\n").slice(0, 6).join("\n       ");
    console.log(`  FAIL ${name}\n       ${message}`);
    results.push({ group, name, ms: Date.now() - start, failure: message });
  }
}
const section = (title) => { group = title; console.log(title); };

/** JUnit XML for CI (SMOKE_JUNIT=path). */
function writeJunit(path, crashed) {
  const esc = (v) => String(v).replace(/[<>&"]/g, (c) => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;", '"': "&quot;" })[c]);
  const cases = results.map((r) => `    <testcase classname="smoke.${esc(r.group)}" name="${esc(r.name)}" time="${r.ms / 1000}">` +
    (r.failure ? `<failure message="${esc(r.failure.split("\n")[0])}">${esc(r.failure)}</failure>` : "") + "</testcase>");
  if (crashed) cases.push(`    <testcase classname="smoke" name="smoke run"><failure message="${esc(crashed.message)}">${esc(crashed.stack)}</failure></testcase>`);
  writeFileSync(path, `<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="backoffice-ui smoke" tests="${cases.length}" failures="${failed + (crashed ? 1 : 0)}">\n${cases.join("\n")}\n</testsuite>\n`);
}
const callsTo = (path) => gateway.calls.filter((c) => c.path === path).length;

let crashed = null;
try {
  for (let i = 0; i < 100; i++) { if (await fetch(`${BASE}/healthz`).then((r) => r.ok, () => false)) break; await new Promise((r) => setTimeout(r, 100)); }
  const browser = await chromium.launch();
  const problems = [];
  async function signedIn(username) {
    const context = await browser.newContext();
    const page = await context.newPage();
    page.on("console", (m) => { if (m.type() === "error") problems.push(`${page.url()}: ${m.text()}`); });
    page.on("pageerror", (e) => problems.push(`${page.url()}: ${e.message}`));
    page.on("dialog", (d) => d.accept());
    await page.goto(`${BASE}/login`);
    await page.getByLabel("Username").fill(username);
    await page.getByLabel("Password").fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.waitForURL(`${BASE}/`);
    return { context, page };
  }
  const cookieHeader = async (context) => (await context.cookies()).map((c) => `${c.name}=${c.value}`).join("; ");

  section("Startup");
  await check("missing configuration stops the server at startup", async () => {
    const bad = spawn(process.execPath, [".next/standalone/server.js"], { env: { PATH: process.env.PATH, PORT: "5197", NODE_ENV: "production" }, stdio: "ignore" });
    const code = await Promise.race([new Promise((r) => bad.on("exit", r)), new Promise((r) => setTimeout(r, 8000, "still running"))]);
    bad.kill();
    assert.equal(code, 1);
  });

  section("Access");
  await check("anonymous: every protected route redirects or answers 401", async () => {
    for (const p of ["/", "/orders", `/orders/${ID.changing}`, "/catalog/1", `/catalog/product/${ID.product}`, "/settlements", "/hcps", "/msrs", `/products/${ID.product}`, "/nope"]) {
      const r = await fetch(BASE + p, { redirect: "manual" });
      assert.equal(r.status, 303, p);
      assert.match(r.headers.get("location"), /^(http:\/\/localhost:5199)?\/login/, p);
    }
    for (const p of ["/bff/session", `/bff/products/${ID.product}/live`, `/bff/orders/${ID.changing}/events`]) assert.equal((await fetch(BASE + p)).status, 401, p);
    assert.equal((await fetch(`${BASE}/orders`, { redirect: "manual", headers: { "x-middleware-subrequest": "src/proxy:src/proxy:src/proxy:src/proxy:src/proxy" } })).status, 303);
    assert.equal((await fetch(`${BASE}/orders`, { redirect: "manual", headers: { RSC: "1", "Next-Router-Prefetch": "1" } })).status, 303);
    assert.equal(gateway.calls.length, 0);
  });
  await check("wrong password: generic message, password cleared, username kept, no cookie", async () => {
    const page = await browser.newPage();
    await page.goto(`${BASE}/login`);
    await page.getByLabel("Username").fill("admin");
    await page.getByLabel("Password").fill("nope");
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.getByRole("alert").filter({ hasText: "Invalid credentials" }).waitFor();
    assert.equal(await page.getByLabel("Password").inputValue(), "");
    assert.equal(await page.getByLabel("Username").inputValue(), "admin");
    assert.equal((await page.context().cookies()).length, 0);
    await page.close();
  });
  await check("sign-in works without JavaScript", async () => {
    const context = await browser.newContext({ javaScriptEnabled: false });
    const page = await context.newPage();
    await page.goto(`${BASE}/settlements`);
    await page.getByLabel("Username").fill("user");
    await page.getByLabel("Password").fill(PASSWORD);
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.waitForURL(`${BASE}/settlements`);
    await page.getByText("March travel").waitFor();
    await context.close();
  });

  const admin = await signedIn("admin");
  const user = await signedIn("user");
  const { page } = admin;

  await check("cookie: HttpOnly, Secure, SameSite=Strict, __Host-; token unreachable from scripts", async () => {
    const [c] = await admin.context.cookies();
    assert.deepEqual([c.name, c.httpOnly, c.secure, c.sameSite], ["__Host-bo_session", true, true, "Strict"]);
    assert.equal(await page.evaluate(() => document.cookie + JSON.stringify({ ...localStorage, ...sessionStorage })), "{}");
    const token = gateway.calls.find((x) => x.user === "admin").token;
    for (const p of ["/", "/orders", "/bff/session"]) assert.ok(!(await (await page.request.get(BASE + p)).text()).includes(token), p);
  });
  await check("the gateway sees each user's own JWT and the real client address, not a forged one", async () => {
    gateway.calls.length = 0;
    await fetch(`${BASE}/orders`, { headers: { cookie: await cookieHeader(user.context), "X-Forwarded-For": "6.6.6.6" } }).then((r) => r.text());
    const c = gateway.calls.find((x) => x.path === "/api/v1/orders/list");
    assert.equal(c.user, "user");
    assert.ok(c.ip && c.ip !== "6.6.6.6" && !c.ip.includes(","), c.ip);
  });

  section("SSG");
  await check("/help: prerendered, public, no gateway calls", async () => {
    gateway.calls.length = 0;
    const r = await fetch(`${BASE}/help`);
    assert.equal(r.headers.get("x-nextjs-cache"), "HIT");
    assert.equal(gateway.calls.length, 0);
  });

  section("ISR");
  const cookie = await cookieHeader(user.context);
  const revalidate = (signature) => {
    const body = '{"type":"product"}', ts = String(Math.floor(Date.now() / 1000));
    signature ??= createHmac("sha256", SECRET).update(`${ts}.${body}`).digest("hex");
    return fetch(`${BASE}/bff/revalidate`, { method: "POST", body, headers: { "x-timestamp": ts, "x-signature": signature } });
  };
  const get = (p, c = cookie) => fetch(BASE + p, { headers: { cookie: c } });
  await check("a failed first build is an error page, and isn't cached", async () => {
    gateway.state.limited = true;
    assert.equal((await get("/catalog/1")).status, 500);
    gateway.state.limited = false;
    assert.equal((await get("/catalog/1")).status, 200);
    await revalidate(); // start the next check with an empty cache
  });
  await check("catalog: first visit builds, later visits (any user) come from the cache", async () => {
    gateway.calls.length = 0;
    const first = await get("/catalog/1"); const a = await first.text();
    const second = await get("/catalog/1", await cookieHeader(admin.context)); const b = await second.text();
    assert.equal(first.status, 200); assert.match(a, /Amoxicillin 500/);
    assert.equal(second.headers.get("x-nextjs-cache"), "HIT");
    assert.equal(a, b, "same bytes for every user");
    assert.equal(callsTo("/api/v1/products/list"), 1);
    assert.equal(gateway.calls.find((c) => c.path === "/api/v1/products/list").user, "catalog");
  });
  await check("cached HTML holds no price, stock or user; never cacheable downstream", async () => {
    for (const p of ["/catalog/1", `/catalog/product/${ID.product}`]) {
      const r = await get(p); const html = await r.text();
      assert.match(html, /Amoxicillin/);
      assert.doesNotMatch(html, /98765|98,765|4242|"username"|>user</, p);
      assert.equal(r.headers.get("cache-control"), "private, no-store", p);
      const rsc = await fetch(BASE + p, { headers: { cookie, RSC: "1" } });
      assert.equal(rsc.headers.get("cache-control"), "private, no-store", `${p} (RSC)`);
    }
  });
  await check("live island: price, stock and user name arrive on the client", async () => {
    await user.page.goto(`${BASE}/catalog/product/${ID.product}`);
    await user.page.getByTestId("live").getByText("98,765.43").waitFor();
    await user.page.getByTestId("live").getByText("4242").waitFor();
    await user.page.getByTestId("user").getByText("user").waitFor();
  });
  await check("stale until asked: an outside change shows after a signed /bff/revalidate", async () => {
    gateway.state.product.name = "Amoxicillin 750";
    assert.match(await (await get("/catalog/1")).text(), /Amoxicillin 500/);
    assert.equal((await revalidate("0".repeat(64))).status, 401);
    assert.match(await (await get("/catalog/1")).text(), /Amoxicillin 500/);
    assert.equal((await revalidate()).status, 200);
    assert.match(await (await get("/catalog/1")).text(), /Amoxicillin 750/);
  });
  await check("an admin's edit refreshes the catalog at once", async () => {
    await page.goto(`${BASE}/products/${ID.product}`);
    await page.getByLabel("Name").fill("Amoxicillin 1000");
    await page.getByLabel("Reason for the change").fill("New strength");
    await page.getByRole("button", { name: "Save" }).click();
    await page.getByText("The catalog pages are refreshed.").waitFor();
    assert.equal(gateway.calls.findLast((c) => c.path === "/api/v1/products/update").body.storageCondition.maxTemperatureC, "8");
    assert.match(await (await get("/catalog/1")).text(), /Amoxicillin 1000/);
    assert.match(await (await get(`/catalog/product/${ID.product}`)).text(), /Amoxicillin 1000/);
  });
  await check("if the gateway fails, the cached catalog still works", async () => {
    gateway.state.limited = true;
    assert.match(await (await get("/catalog/1")).text(), /Amoxicillin 1000/);
    gateway.state.limited = false;
  });
  await check("unknown catalog page and bad ids are 404", async () => {
    for (const p of ["/catalog/99", "/catalog/abc", "/catalog/product/nope", "/orders/nope", `/orders/${ID.msr}`]) assert.equal((await get(p)).status, 404, p);
  });

  section("SSR");
  await check("strict CSP: fresh nonce per response, on every script; no store", async () => {
    const nonces = [];
    for (let i = 0; i < 2; i++) {
      const r = await get("/orders"); const html = await r.text();
      const csp = r.headers.get("content-security-policy");
      assert.doesNotMatch(csp, /unsafe/);
      const nonce = /'nonce-([^']+)'/.exec(csp)[1];
      nonces.push(nonce);
      for (const tag of html.match(/<script\b[^>]*>/g)) assert.ok(tag.includes(`nonce="${nonce}"`), tag);
      assert.match(r.headers.get("cache-control"), /no-store/);
    }
    assert.notEqual(nonces[0], nonces[1]);
    assert.match((await fetch(`${BASE}/login`)).headers.get("content-security-policy"), /nonce-/);
  });
  await check("dashboard streams both cards; HTML is complete without client fetches", async () => {
    const html = await (await get("/")).text();
    assert.match(html, /Deliveries waiting for Quality/); assert.match(html, /March travel/);
  });
  await check("roles: a regular user sees no admin controls and gets 403 pages", async () => {
    const html = await (await get(`/orders/${ID.quarantined}`)).text();
    assert.doesNotMatch(html, /Release the delivery|Audit trail/);
    assert.equal((await get("/products/new")).status, 403);
  });
  await check("order status: SSE moves the page without a reload", async () => {
    await page.goto(`${BASE}/orders/${ID.changing}`);
    await page.getByText("Waiting for updates…").waitFor();
    await page.evaluate(() => { window.__same = true; });
    Object.assign(gateway.state.orders[0], { status: "PENDING_APPROVAL" });
    await page.getByText("Pending approval").waitFor({ timeout: 15_000 });
    Object.assign(gateway.state.orders[0], { status: "APPROVED", approvedBy: "agent", approvedAt: "2026-03-02T11:00:00" });
    await page.getByText("by agent").waitFor({ timeout: 15_000 });
    assert.equal(await page.evaluate(() => window.__same), true);
  });
  await check("closing the page stops the polling", async () => {
    Object.assign(gateway.state.orders[0], { status: "AWAITING_STOCK" });
    const tab = await admin.context.newPage();
    await tab.goto(`${BASE}/orders/${ID.changing}`);
    await tab.getByText("Waiting for updates…").waitFor();
    await new Promise((r) => setTimeout(r, 2500));
    await tab.close();
    await new Promise((r) => setTimeout(r, 500));
    const before = callsTo(`/api/v1/orders/${ID.changing}`);
    await new Promise((r) => setTimeout(r, 6000));
    assert.equal(callsTo(`/api/v1/orders/${ID.changing}`), before);
    Object.assign(gateway.state.orders[0], { status: "APPROVED" });
  });
  await check("Quality releases a delivery; audit trail shown to admins", async () => {
    await page.goto(`${BASE}/orders/${ID.quarantined}`);
    await page.getByRole("heading", { name: "Audit trail" }).waitFor();
    const form = page.locator("section", { has: page.getByRole("heading", { name: "Release the delivery" }) });
    await form.getByLabel("Reason").fill("Stability data reviewed");
    await form.getByRole("button", { name: "Release" }).click();
    await page.getByText("Stability data reviewed").waitFor();
    await page.getByText("Delivered", { exact: true }).first().waitFor();
  });
  await check("lots: quarantine with a reason, and recall recipients", async () => {
    await page.goto(`${BASE}/products/${ID.product}/lots`);
    await page.getByText("Decide").click();
    await page.getByLabel("Reason").fill("Investigation");
    await page.getByRole("button", { name: "Record" }).click();
    await page.getByText("Quarantined", { exact: true }).waitFor();
    assert.deepEqual(gateway.calls.findLast((c) => c.path.endsWith("/lots/quarantine")).body, { batchNumber: "L-1", reason: "Investigation" });
    await page.getByRole("link", { name: "Who received it?" }).click();
    await page.getByText("Orders that received batch L-1").waitFor();
  });
  await check("settlement: create, reject a bad invoice (keeping input), upload a 9 MB file", async () => {
    await page.goto(`${BASE}/settlements/new`);
    await page.getByLabel("Description").fill("April travel");
    await page.getByLabel("Date").fill("2026-04-30");
    await page.getByLabel("Sales rep").selectOption({ label: "Marta Ruiz" });
    await page.getByRole("button", { name: "Create" }).click();
    await page.waitForURL(/\/settlements\/7777/);
    await page.getByLabel("Invoice number").fill("bad");
    await page.getByLabel("Amount").fill("120.50");
    await page.getByLabel("Issue date").fill("2026-04-02");
    await page.getByLabel("File").setInputFiles({ name: "invoice.pdf", mimeType: "application/pdf", buffer: Buffer.alloc(9 * 1024 * 1024, 65) });
    await page.getByRole("button", { name: "Add invoice" }).click();
    await page.getByText("One letter and 12 digits, e.g.").waitFor();
    assert.equal(await page.getByLabel("Amount").inputValue(), "120.50");
    await page.getByLabel("Invoice number").fill("A000100000001");
    await page.getByRole("button", { name: "Add invoice" }).click();
    await page.getByRole("cell", { name: "A000100000001" }).waitFor();
    await page.getByText("invoice.pdf").waitFor();
    assert.ok(gateway.calls.findLast((c) => c.path.endsWith("/invoice/add")).body.length > 9 * 1024 * 1024);
  });
  await check("HCP edit: specialties sent as codes, with a reason", async () => {
    await page.goto(`${BASE}/hcps/${ID.hcp}`);
    assert.ok(await page.getByLabel("Cardiology").isChecked());
    await page.getByLabel("Cardiology").uncheck();
    await page.getByLabel("Dermatology").check();
    await page.getByLabel("Reason for the change").fill("Moved ward");
    await page.getByRole("button", { name: "Save" }).click();
    await page.getByText("Saved.").waitFor();
    const { body } = gateway.calls.findLast((c) => c.path === "/api/v1/healthcareprof/update");
    assert.deepEqual([body.specialties, body.reason], [["DERM"], "Moved ward"]);
    await page.reload();
    assert.ok(await page.getByLabel("Dermatology").isChecked());
  });
  await check("no CSP violations or script errors on any page so far", () => {
    const real = problems.filter((p) => !/status of 4\d\d/.test(p));
    assert.deepEqual(real, []);
  });

  section("Failure and session end");
  await check("rate limited: a clear message, not a crash", async () => {
    gateway.state.limited = true;
    await page.goto(`${BASE}/orders`);
    await page.getByText("Too many requests. Please wait a minute").waitFor();
    gateway.state.limited = false;
  });
  await check("cross-site Server Action posts are refused", async () => {
    const html = await (await get("/settlements/new")).text();
    const form = new FormData();
    for (const [, n, v] of html.matchAll(/<input type="hidden" name="(\$ACTION[^"]*)"(?: value="([^"]*)")?/g)) form.set(n, (v ?? "").replaceAll("&quot;", '"'));
    form.set("description", "csrf"); form.set("settlementDate", "2026-01-01"); form.set("medicalSalesRepId", ID.msr);
    const before = callsTo("/api/v1/settlement/create");
    const evil = await fetch(`${BASE}/settlements/new`, { method: "POST", body: form, redirect: "manual", headers: { cookie, Origin: "https://evil.example" } });
    assert.equal(callsTo("/api/v1/settlement/create"), before, `status ${evil.status}`);
    await fetch(`${BASE}/settlements/new`, { method: "POST", body: form, redirect: "manual", headers: { cookie, Origin: BASE } });
    assert.equal(callsTo("/api/v1/settlement/create"), before + 1, "control: the same post from our own origin works");
  });
  await check("a JWT the gateway refuses ends the session", async () => {
    gateway.revoked.add(gateway.calls.findLast((c) => c.user === "user").token);
    await user.page.goto(`${BASE}/orders`);
    await user.page.waitForURL(`${BASE}/login?reason=expired`);
    assert.equal((await user.context.cookies()).length, 0);
  });
  await check("sign-out clears the cookie and protects Back", async () => {
    await page.goto(`${BASE}/settlements`);
    await page.getByRole("button", { name: "Sign out" }).click();
    await page.waitForURL(`${BASE}/login`);
    assert.equal((await admin.context.cookies()).length, 0);
    await page.goto(`${BASE}/settlements`);
    assert.match(page.url(), /\/login\?next=%2Fsettlements/);
  });
  if (process.env.SHOTS) {
    const shots = await signedIn("admin");
    Object.assign(gateway.state.orders[1], { status: "DELIVERY_QUARANTINED" });
    for (const [name, p] of [["dashboard", "/"], ["orders", "/orders"], ["order", `/orders/${ID.quarantined}`], ["catalog", `/catalog/product/${ID.product}`], ["settlement", "/settlements/77777777-7777-4777-8777-777777777777"]]) {
      await shots.page.goto(BASE + p); await shots.page.waitForTimeout(500);
      await shots.page.screenshot({ path: `${process.env.SHOTS}/${name}.png`, fullPage: true });
    }
    await shots.page.setViewportSize({ width: 390, height: 800 });
    await shots.page.goto(`${BASE}/orders`);
    await shots.page.screenshot({ path: `${process.env.SHOTS}/mobile.png` });
    console.log(`  mobile overflow: ${await shots.page.evaluate(() => document.documentElement.scrollWidth - innerWidth)}`);
  }
  await browser.close();
} catch (e) {
  crashed = e;
  console.log(`  CRASH ${e.stack}`);
} finally {
  server.kill();
  gateway.close();
}
console.log(`\n${passed} passed, ${failed} failed`);
if (process.env.SMOKE_JUNIT) writeJunit(process.env.SMOKE_JUNIT, crashed);
if (failed || crashed) { console.log(log.slice(-3000)); process.exit(1); }
process.exit(0);

// Production smoke test: real Chromium against the built PWA with the nginx security headers.
// Run: npx playwright install chromium && npm run smoke
import { chromium } from "playwright";
import { server } from "./server.mjs";

await new Promise((resolve) => server.listen(4180, resolve));

const base = "http://localhost:4180";
const browser = await chromium.launch();
const page = await browser.newPage();
const problems = [];
page.on("console", (m) => { if (m.type() === "error" || m.type() === "warning") problems.push(`${m.type()}: ${m.text()}`); });
page.on("pageerror", (e) => problems.push(`pageerror: ${e.message}`));
const check = (label, ok) => { console.log(`${ok ? "PASS" : "FAIL"}  ${label}`); if (!ok) process.exitCode = 1; };

await page.goto(base + "/visits");
await page.getByLabel("Username").waitFor();
check("login screen renders under the strict CSP", true);

await page.getByLabel("Username").fill("rep1");
await page.getByLabel("Password").fill("secret");
await page.getByRole("button", { name: "Sign in" }).click();
await page.getByText("Ana Gómez").waitFor();
check("after login the visit list shows HCP names from the directory", await page.getByText("Rita Paz · Site S-9").isVisible());

await page.getByRole("link", { name: "+ New" }).click();
await page.getByRole("button", { name: /healthcare professional/i }).click();
await page.getByRole("combobox").fill("bru");
await page.keyboard.press("Enter");
check("HCP picker searches and selects", await page.getByRole("button", { name: /healthcare professional/i }).textContent().then((t) => t.includes("Bruno Díaz")));

await page.getByRole("link", { name: "Settlements" }).click();
await page.getByText("Q3 expenses").waitFor();
check("settlements tab lists settlements", true);

const swOk = await page.evaluate(async () => {
  const reg = await navigator.serviceWorker.getRegistration();
  if (!reg) return false;
  await navigator.serviceWorker.ready;
  return true;
});
check("service worker registers (Trusted Types default policy allows same-origin script URL)", swOk);

const before = problems.length;
const ttResult = await page.evaluate(() => { try { document.body.insertAdjacentHTML("beforeend", "<b>x</b>"); return "allowed"; } catch { return "blocked"; } });
await page.waitForTimeout(200);
problems.splice(before); // the probe's own (expected) violation report
check("Trusted Types blocks raw HTML injection", ttResult === "blocked");

const storage = await page.evaluate(() => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }));
check("token is not in localStorage/sessionStorage", !storage.includes("jwt-e2e"));

// Reload twice so the now-active service worker controls the page and serves API calls.
await page.reload();
await page.getByLabel("Username").waitFor();
check("reload ends the in-memory session (login shown again)", true);
await page.getByLabel("Username").fill("rep1");
await page.getByLabel("Password").fill("secret");
await page.getByRole("button", { name: "Sign in" }).click();
await page.getByText("Q3 expenses").waitFor();
const cached = await page.evaluate(async () => {
  const urls = [];
  for (const name of await caches.keys()) for (const req of await (await caches.open(name)).keys()) urls.push(req.url);
  return { controlled: !!navigator.serviceWorker.controller, total: urls.length, api: urls.filter((u) => /\/(api|auth)\//.test(u)) };
});
check(`page is controlled by the service worker (${cached.total} app-shell entries precached)`, cached.controlled && cached.total > 0);
check("no API responses in Cache Storage", cached.api.length === 0);

await page.getByRole("button", { name: "Sign out" }).click();
await page.getByLabel("Username").waitFor();
check("sign out returns to login", true);

const csp = problems.filter((p) => /Content Security Policy|Trusted Type|TrustedScriptURL|TrustedHTML/i.test(p));
check(`no CSP / Trusted Types violations logged (${csp.length})`, csp.length === 0);
if (problems.length) console.log("console:\n  " + problems.join("\n  "));
await browser.close();
server.close();

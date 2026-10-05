// Serves dist/ with the production security headers (parsed from nginx/security-headers.conf)
// and a mock gateway that rejects calls without the bearer token.
// HTTP-only adjustments for localhost: no HSTS, no upgrade-insecure-requests.
import { createServer } from "node:http";
import { readFileSync, existsSync, statSync } from "node:fs";
import { join, extname, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const PWA = join(dirname(fileURLToPath(import.meta.url)), "..");
const DIST = join(PWA, "dist");
const headers = {};
for (const m of readFileSync(join(PWA, "nginx/security-headers.conf"), "utf8").matchAll(/^add_header ([\w-]+) "(.*)" always;$/gm)) {
  headers[m[1]] = m[2].replace("$api_origin", "").replace("upgrade-insecure-requests; ", "");
}
delete headers["Strict-Transport-Security"];
export const CSP = headers["Content-Security-Policy"];

const types = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".png": "image/png", ".svg": "image/svg+xml", ".webmanifest": "application/manifest+json", ".txt": "text/plain" };
const json = (res, status, body) => { res.writeHead(status, { ...headers, "Content-Type": "application/json", "Cache-Control": "no-store" }); res.end(JSON.stringify(body)); };
export const apiLog = [];

export const server = createServer((req, res) => {
  const url = new URL(req.url, "http://localhost");
  if (url.pathname.startsWith("/auth/") || url.pathname.startsWith("/api/")) {
    apiLog.push(`${req.method} ${url.pathname} auth=${req.headers.authorization ? "bearer" : "none"}`);
    if (url.pathname === "/auth/login") return json(res, 200, { token: "jwt-e2e", username: "rep1", roles: ["MSR"] });
    if (req.headers.authorization !== "Bearer jwt-e2e") return json(res, 401, { statusCode: 401, message: "Authentication failed" });
    switch (url.pathname) {
      case "/api/v1/healthcareprof/list": return json(res, 200, [{ id: "h1", name: "Ana", surname: "Gómez", email: "ana@clinic.org", active: true, specialties: ["Cardiology"] }, { id: "h2", name: "Bruno", surname: "Díaz", email: "b@clinic.org", active: true, specialties: ["Pediatrics"] }]);
      case "/api/v1/medicalsalesrep/list": return json(res, 200, [{ id: "m1", name: "Rita", surname: "Paz", email: "rita@pharma.com", active: true }]);
      case "/api/v1/visit/list": return json(res, 200, [{ id: "v1", visitDate: "2026-10-01T00:00:00", healthCareProfId: "h1", medicalSalesRepId: "m1", visitSiteId: "S-9", productPromoAttachments: [] }]);
      case "/api/v1/settlement/list": return json(res, 200, [{ id: "s1", description: "Q3 expenses", settlementDate: "2026-09-30", status: "DRAFT", totalAmount: 1234.56, invoices: [], medicalSalesRepId: "m1" }]);
      default: return json(res, 404, { statusCode: 404, message: "Not found" });
    }
  }
  let file = join(DIST, url.pathname);
  if (!existsSync(file) || statSync(file).isDirectory()) file = join(DIST, "index.html");
  res.writeHead(200, { ...headers, "Content-Type": types[extname(file)] ?? "application/octet-stream" });
  res.end(readFileSync(file));
});

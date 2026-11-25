// Stands in for the API gateway: same routes, bodies, status codes and role rules as the services.
import http from "node:http";

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
const USERS = { user: ["ROLE_USER"], admin: ["ROLE_USER", "ROLE_ADMIN"], catalog: ["ROLE_USER"] };
export const PASSWORD = "correct-horse";
export const ID = {
  msr: "11111111-1111-4111-8111-111111111111", hcp: "22222222-2222-4222-8222-222222222222",
  product: "33333333-3333-4333-8333-333333333333", changing: "44444444-4444-4444-8444-444444444444",
  quarantined: "55555555-5555-4555-8555-555555555555", settlement: "66666666-6666-4666-8666-666666666666",
};

export function startMockGateway(port) {
  const calls = [];
  const revoked = new Set();
  const state = {
    limited: false,
    product: { id: ID.product, name: "Amoxicillin 500", description: "Capsules", price: 98765.43, unit: "box", stock: 4242, active: true, gtin: "09506000134352", lotTracked: true,
      storageCondition: { minTemperatureC: 2, maxTemperatureC: 8, maxHumidityPercent: null, protectFromLight: true, coldChain: true, description: "2.0 to 8.0 °C" } },
    lots: [{ batchNumber: "L-1", expirationDate: "2030-01-31", quantity: 10, status: "AVAILABLE", receivedAt: "2026-01-01T00:00:00Z", expired: false, allocatable: true }],
    msr: { id: ID.msr, name: "Marta", surname: "Ruiz", email: "marta@example.com", active: true },
    hcp: { id: ID.hcp, name: "Hugo", surname: "Paz", email: "hugo@example.com", active: true, specialties: ["Cardiology"] },
    settlements: [{ id: ID.settlement, description: "March travel", settlementDate: "2026-03-31", status: "OPEN", totalAmount: 0, invoices: [], medicalSalesRepId: ID.msr }],
    orders: [],
  };
  const order = (id, status, extra = {}) => ({ id, medicalSalesRepId: ID.msr, status, lines: [{ productId: ID.product, productName: "Amoxicillin 500", quantity: 2, unitPrice: 10, lineTotal: 20, storageCondition: null }],
    totalAmount: 20, approvedBy: null, rejectedBy: null, rejectionReason: null, createdAt: "2026-03-01T10:00:00", approvedAt: null, rejectedAt: null, deliveredAt: null, transportCheckRequired: true, deliveryCheck: null, ...extra });
  state.orders.push(order(ID.changing, "AWAITING_STOCK", { createdAt: "2026-03-02T10:00:00" }),
    order(ID.quarantined, "DELIVERY_QUARANTINED", { deliveryCheck: { conditionsMaintained: false, minTemperatureC: 1, maxTemperatureC: 15.5, maxHumidityPercent: null, lightExposed: false, note: null, breaches: ["15.5 °C above 8.0 °C"], decidedBy: null, decisionReason: null, decidedAt: null } }));

  const server = http.createServer(async (req, res) => {
    const chunks = [];
    for await (const c of req) chunks.push(c);
    const raw = Buffer.concat(chunks).toString("latin1");
    const url = new URL(req.url, "http://x");
    const path = url.pathname;
    const isJson = req.headers["content-type"]?.includes("json");
    const body = isJson ? JSON.parse(raw) : raw;
    const send = (status, data) => { res.writeHead(status, { "Content-Type": "application/json" }); res.end(data === undefined ? "" : JSON.stringify(data)); };

    if (path === "/auth/login") {
      calls.push({ method: req.method, path, ip: req.headers["x-forwarded-for"] });
      const roles = USERS[body.username];
      if (!roles || body.password !== PASSWORD) return send(401, { error: "Invalid credentials" });
      const exp = Math.floor(Date.now() / 1000) + 3600;
      return send(200, { token: `${b64({ alg: "HS256" })}.${b64({ sub: body.username, authorities: roles, exp, jti: Math.random() })}.sig`, username: body.username, roles });
    }
    const token = req.headers.authorization?.replace("Bearer ", "");
    if (!token || revoked.has(token)) return send(401, { error: "Unauthorized", message: "Invalid or expired token" });
    const who = JSON.parse(Buffer.from(token.split(".")[1], "base64url"));
    calls.push({ method: req.method, path, user: who.sub, token, body, ip: req.headers["x-forwarded-for"] });
    if (state.limited) return send(429, { error: "Too Many Requests" });
    const admin = who.authorities.includes("ROLE_ADMIN");
    const p = path.replace("/api/v1", "");
    const id = p.split("/")[2];
    const adminOnly = /\/history$|activate$|\/delivery\/|lots\/(quarantine|lift|reject|recipients)|products\/(create|update)/.test(p);
    if (adminOnly && !admin) return send(403, { statusCode: 403, message: "Access denied" });

    if (p.endsWith("/history")) return send(200, [{ version: 1, eventType: "created", occurredAt: "2026-03-01T10:00:00Z", actor: "ana", correlationId: null, reason: null, changes: null, data: {} }]);
    if (p === "/medicalsalesrep/list") return send(200, [state.msr]);
    if (p === "/healthcareprof/list") return send(200, [state.hcp]);
    if (p === "/healthcareprof/specialties") return send(200, [{ code: "CARD", name: "Cardiology" }, { code: "DERM", name: "Dermatology" }]);
    if (p === "/healthcareprof/update") { state.hcp = { ...state.hcp, ...body, specialties: ["Dermatology"] }; return send(200, state.hcp); }
    if (p === `/healthcareprof/${ID.hcp}`) return send(200, state.hcp);
    if (p === `/medicalsalesrep/${ID.msr}`) return send(200, state.msr);
    if (p === "/products/list") return send(200, [state.product]);
    if (p === "/products/update") { Object.assign(state.product, { name: body.name, price: Number(body.price) }); return send(200, state.product); }
    if (p === `/products/${ID.product}`) return send(200, state.product);
    if (p === `/products/${ID.product}/lots`) return send(200, state.lots);
    if (p.endsWith("/lots/recipients")) return send(200, [{ orderId: ID.quarantined, reservationStatus: "CONFIRMED", quantity: 2 }]);
    if (p.endsWith("/lots/quarantine")) { state.lots[0].status = "QUARANTINED"; return send(200, state.lots[0]); }
    if (p === "/orders/list") return send(200, state.orders);
    const o = p.startsWith("/orders/") && state.orders.find((x) => x.id === id);
    if (o && p.endsWith("/delivery/release")) { Object.assign(o, { status: "DELIVERED" }); Object.assign(o.deliveryCheck, { decidedBy: who.sub, decisionReason: body.reason, decidedAt: "2026-03-03T10:00:00" }); return send(200, o); }
    if (o) return send(200, o);
    if (p === "/settlement/list") return send(200, url.searchParams.get("page") === "1" ? state.settlements : []);
    if (p === "/settlement/create") { const s = { id: "77777777-7777-4777-8777-777777777777", status: "OPEN", totalAmount: 0, ...body }; state.settlements.push(s); return send(201, s); }
    if (p === "/settlement/invoice/add") {
      const field = (n) => new RegExp(`name="${n}"\\r\\n\\r\\n([^\\r]*)`).exec(raw)?.[1];
      const s = state.settlements.find((x) => x.id === field("settlementId"));
      s.invoices.push({ id: "88888888-8888-4888-8888-888888888888", invoiceNumber: field("invoiceNumber"), issueDate: field("issueDate"), dueDate: null, amount: Number(field("amount")), status: "DRAFT", fileName: /filename="([^"]+)"/.exec(raw)?.[1], contentType: null, sizeInBytes: 1, sha256Hash: "ab".repeat(32) });
      return send(201, s.invoices.at(-1));
    }
    const s = p.startsWith("/settlement/") && state.settlements.find((x) => x.id === id);
    if (s) return send(200, s);
    return send(404, { statusCode: 404, message: "Not found" });
  });
  return new Promise((resolve) => server.listen(port, "127.0.0.1", () => resolve({ calls, revoked, state, close: () => server.close() })));
}

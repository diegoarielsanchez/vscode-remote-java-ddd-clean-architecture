// Concurrent stock reservation and login throughput, through the API gateway. See load-test/README.md.
//
//   stock_race  many orders for the same product at once: checks that stock never goes negative or leaks, that no
//               order is rejected while stock remains, that lots are taken first expiry first out (FEFO), and that
//               every reservation is in the audit trail
//   logins      ramps POST /auth/login to find where password hashing saturates identity-service
//
// Needs the load-test overlay (no gateway rate limit), like events-and-cache.js.

import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const STOCK_ROUNDS = Number(__ENV.STOCK_ROUNDS || 0);              // products raced for, one after another
const STOCK_UNITS = Number(__ENV.STOCK_UNITS || 200);              // units received per product
const ORDER_COUNT = Number(__ENV.ORDER_COUNT || 60);               // orders per product, sent at once
const ORDER_QTY = Number(__ENV.ORDER_QTY || 5);                    // units per order
const SETTLE_SECONDS = Number(__ENV.SETTLE_SECONDS || 120);        // time allowed for the saga to answer them all
const LOGIN_PEAK_RATE = Number(__ENV.LOGIN_PEAK_RATE || 0);        // logins per second, at peak
const RAMP_SECONDS = Number(__ENV.RAMP_SECONDS || 120);
const HOLD_SECONDS = Number(__ENV.HOLD_SECONDS || 60);
const WITH_AGENT = Boolean(__ENV.AGENT_USERNAME);                  // a second lot needs the agent's restock

const DAY_MS = 24 * 60 * 60 * 1000;
const JSON_TYPE = 'application/json';
const STOCK_RESERVED = 'catalog.product.stock-reserved';

const rateLimited = new Counter('rate_limited');                   // the overlay's limit override is missing
const gatewayFallback = new Counter('gateway_fallback');           // circuit breaker or its timeout tripped
const invariantViolations = new Counter('stock_invariant_violations');
const ordersUnsettled = new Counter('orders_unsettled');           // still AWAITING_STOCK after SETTLE_SECONDS
const settleTime = new Trend('reservation_settle_ms', true);       // first order sent → last order answered

const scenarios = {};
if (STOCK_ROUNDS > 0) {
  scenarios.stock_race = {
    executor: 'per-vu-iterations',
    exec: 'stockRace',
    vus: 1,                                                        // the concurrency comes from http.batch
    iterations: STOCK_ROUNDS,
    maxDuration: `${STOCK_ROUNDS * (SETTLE_SECONDS + 60)}s`,
  };
}
if (LOGIN_PEAK_RATE > 0) {
  scenarios.logins = {
    executor: 'ramping-arrival-rate',
    exec: 'login',
    startRate: 1,
    timeUnit: '1s',
    preAllocatedVUs: Math.max(20, LOGIN_PEAK_RATE * 2),
    maxVUs: LOGIN_PEAK_RATE * 10,                                  // a login can take 1s or more once saturated
    stages: [
      { target: LOGIN_PEAK_RATE, duration: `${RAMP_SECONDS}s` },
      { target: LOGIN_PEAK_RATE, duration: `${HOLD_SECONDS}s` },
    ],
  };
}
if (Object.keys(scenarios).length === 0) {
  throw new Error('Set at least one of STOCK_ROUNDS, LOGIN_PEAK_RATE');
}

export const options = {
  scenarios,
  batch: ORDER_COUNT,                                              // send every order of a round at once
  batchPerHost: ORDER_COUNT,
  setupTimeout: '5m',
  thresholds: {
    rate_limited: [{ threshold: 'count==0', abortOnFail: true }],
    stock_invariant_violations: ['count==0'],
    orders_unsettled: ['count==0'],
    'http_req_failed{scenario:logins}': ['rate<0.01'],
    'http_req_duration{scenario:logins}': ['p(95)<1000'],
  },
};

function loginAs(username, password, tags) {
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({ username, password }),
    { headers: { 'Content-Type': JSON_TYPE }, tags });
  if (res.status === 429) rateLimited.add(1);
  return res;
}

function tokenFor(username, password, who) {
  const res = loginAs(username, password);
  if (res.status !== 200) throw new Error(`Login as ${who} failed: HTTP ${res.status}`);
  return res.json('token');
}

function params(token) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': JSON_TYPE } };
}

function track(res) {
  if (res.status === 429) rateLimited.add(1);
  if (res.status === 503) gatewayFallback.add(1);
  return res;
}

function post(path, body, token) {
  return track(http.post(`${BASE_URL}${path}`, body === null ? null : JSON.stringify(body), params(token)));
}

function get(path, token) {
  return track(http.get(`${BASE_URL}${path}`, params(token)));
}

function isoDate(daysAhead) {
  return new Date(Date.now() + daysAhead * DAY_MS).toISOString().slice(0, 10);
}

export function setup() {
  const data = { runTag: Date.now().toString(36) };
  if (STOCK_ROUNDS > 0) {
    if (!__ENV.ADMIN_USERNAME) throw new Error('STOCK_ROUNDS needs ADMIN_USERNAME and ADMIN_PASSWORD');
    data.userToken = tokenFor(__ENV.USERNAME, __ENV.PASSWORD, 'the seed user');
    const claims = JSON.parse(encoding.b64decode(data.userToken.split('.')[1], 'rawurl', 's'));
    if (!claims.msr_id) {
      throw new Error('STOCK_ROUNDS needs a user with an msr_id claim: set IDENTITY_SEED_MSR_ID to an active MSR');
    }
    data.adminToken = tokenFor(__ENV.ADMIN_USERNAME, __ENV.ADMIN_PASSWORD, 'the admin');
    if (WITH_AGENT) data.agentToken = tokenFor(__ENV.AGENT_USERNAME, __ENV.AGENT_PASSWORD, 'the agent');
  }
  return data;
}

// A fresh, active product per round. With the agent's credentials it holds two lots, and the one received last
// expires first, so FEFO and "first received, first out" would pick different lots.
function receiveProduct(data, round) {
  const earlyQty = WITH_AGENT ? Math.floor(STOCK_UNITS / 2) : 0;
  const late = { batchNumber: `LT-${data.runTag}-${round}-L`, expirationDate: isoDate(3 * 365), quantity: STOCK_UNITS - earlyQty };
  const early = { batchNumber: `LT-${data.runTag}-${round}-E`, expirationDate: isoDate(2 * 365), quantity: earlyQty };

  const created = post('/api/v1/products/create', {
    name: `Load test ${data.runTag} round ${round}`,
    price: 1.0,
    unit: 'box',
    initialStock: late.quantity,
    batchNumber: late.batchNumber,
    expirationDate: late.expirationDate,
    lotTracked: true,
    storageCondition: { minTemperatureC: 15, maxTemperatureC: 25 },
  }, data.adminToken);
  if (created.status !== 201) throw new Error(`Creating the product failed: HTTP ${created.status} ${created.body}`);
  const id = created.json('id');

  const activated = post(`/api/v1/products/${id}/activate`, null, data.adminToken);
  if (activated.status !== 200) throw new Error(`Activating ${id} failed: HTTP ${activated.status} ${activated.body}`);

  if (WITH_AGENT) {
    const restocked = post(`/api/v1/products/${id}/restock`, {
      quantity: early.quantity, batchNumber: early.batchNumber, expirationDate: early.expirationDate,
    }, data.agentToken);
    if (restocked.status !== 200) throw new Error(`Restocking ${id} failed: HTTP ${restocked.status} ${restocked.body}`);
  }
  return { id, late, early };
}

export function stockRace(data) {
  const round = exec.scenario.iterationInTest;
  const violation = (message) => {
    invariantViolations.add(1);
    console.error(`[round ${round}] ${message}`);
  };
  const product = receiveProduct(data, round);

  // Every order at once: the race happens in product-catalog-service, as the order.created events arrive.
  const order = JSON.stringify({ lines: [{ productId: product.id, quantity: ORDER_QTY }] });
  const sentAt = Date.now();
  const accepted = http.batch(Array.from({ length: ORDER_COUNT },
    () => ['POST', `${BASE_URL}/api/v1/orders/create`, order, params(data.userToken)])).map(track);
  const orderIds = accepted.filter((r) => r.status === 202).map((r) => r.json('id'));
  check(orderIds, { 'every order accepted': (ids) => ids.length === ORDER_COUNT });

  // Wait for the saga's answer to each order.
  const statuses = {};
  let pending = orderIds;
  const deadline = sentAt + SETTLE_SECONDS * 1000;
  while (pending.length > 0 && Date.now() < deadline) {
    sleep(1);
    const answers = http.batch(pending.map((id) => ['GET', `${BASE_URL}/api/v1/orders/${id}`, null, params(data.userToken)]));
    const stillWaiting = [];
    answers.forEach((r, i) => {
      track(r);
      const status = r.status === 200 ? r.json('status') : null;
      if (status === null || status === 'AWAITING_STOCK') stillWaiting.push(pending[i]);
      else statuses[pending[i]] = status;
    });
    pending = stillWaiting;
  }
  if (pending.length > 0) {
    ordersUnsettled.add(pending.length);
    console.error(`[round ${round}] ${pending.length} orders still AWAITING_STOCK after ${SETTLE_SECONDS}s; `
      + 'stock checks skipped for this round');
    return;
  }
  settleTime.add(Date.now() - sentAt);

  const counts = {};
  Object.values(statuses).forEach((s) => { counts[s] = (counts[s] || 0) + 1; });
  const reservedOrders = counts.PENDING_APPROVAL || 0;
  const rejectedOrders = counts.STOCK_REJECTED || 0;
  if (reservedOrders + rejectedOrders !== orderIds.length) {
    violation(`unexpected order statuses: ${JSON.stringify(counts)}`);
  }

  const lotsRes = get(`/api/v1/products/${product.id}/lots`, data.userToken);
  if (lotsRes.status !== 200) {
    violation(`reading the lots failed: HTTP ${lotsRes.status}`);
    return;
  }
  const lots = {};
  lotsRes.json().forEach((l) => { lots[l.batchNumber] = l.quantity; });
  const onHand = Object.values(lots).reduce((a, b) => a + b, 0);

  // Stock never goes negative.
  Object.keys(lots).filter((b) => lots[b] < 0).forEach((b) => violation(`lot ${b} holds ${lots[b]} units`));

  // Every unit is either still on hand or reserved by an accepted order: nothing lost, nothing sold twice.
  if (onHand + reservedOrders * ORDER_QTY !== STOCK_UNITS) {
    violation(`received ${STOCK_UNITS} units, but ${onHand} on hand + ${reservedOrders} orders × ${ORDER_QTY} reserved`);
  }

  // Stock only goes down in this test, so stock left at the end was there when an order was turned away.
  if (rejectedOrders > 0 && onHand >= ORDER_QTY) {
    violation(`${rejectedOrders} orders rejected for stock while ${onHand} units remain (allocator gave up on retries?)`);
  }

  // FEFO: the lot expiring first is used up before the other one is touched.
  if (WITH_AGENT) {
    const earlyLeft = lots[product.early.batchNumber];
    const lateTaken = product.late.quantity - lots[product.late.batchNumber];
    if (lateTaken > 0 && earlyLeft > 0) {
      violation(`FEFO broken: ${lateTaken} units taken from the later lot while ${earlyLeft} remain in the earlier one`);
    }
  }

  // Audit trail (ALCOA+): one stock-reserved entry per accepted order.
  const history = get(`/api/v1/products/${product.id}/history`, data.adminToken);
  if (history.status !== 200) {
    violation(`reading the audit trail failed: HTTP ${history.status}`);
  } else {
    const audited = history.json().filter((e) => e.eventType === STOCK_RESERVED).length;
    if (audited !== reservedOrders) {
      violation(`${reservedOrders} orders reserved stock, but the audit trail has ${audited} ${STOCK_RESERVED} entries`);
    }
  }

  console.log(`[round ${round}] ${reservedOrders} reserved, ${rejectedOrders} rejected, ${onHand} units left, `
    + `lots ${JSON.stringify(lots)}`);
}

export function login() {
  const res = loginAs(__ENV.USERNAME, __ENV.PASSWORD, { name: 'login' });
  if (res.status === 503) gatewayFallback.add(1);
  check(res, { 'logged in': (r) => r.status === 200 });
}

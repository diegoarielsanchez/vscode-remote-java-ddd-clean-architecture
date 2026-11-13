// Drives RabbitMQ and Redis through the API gateway. See load-test/README.md.
//
//   hcp_events / msr_events  each create writes an outbox row, published to hcp.events / msr.events
//   visit_plans              each create reads the hcpActiveStatus and msrActiveStatus caches

import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HCP_COUNT = Number(__ENV.HCP_COUNT || 100);        // cache keys in play: one per seeded HCP
const PLAN_RATE = Number(__ENV.PLAN_RATE || 0);          // visit plans per second
const HCP_PEAK_RATE = Number(__ENV.HCP_PEAK_RATE || 0);  // HCP creates per second, at peak
const MSR_PEAK_RATE = Number(__ENV.MSR_PEAK_RATE || 0);  // MSR creates per second, at peak
const RAMP_SECONDS = Number(__ENV.RAMP_SECONDS || 300);
const HOLD_SECONDS = Number(__ENV.HOLD_SECONDS || 120);

const DAY_MS = 24 * 60 * 60 * 1000;
const TOKEN_MAX_AGE_MS = 50 * 60 * 1000;                 // tokens last 1 hour (jwt.expiration)
const VISIT_SITE_ID = '00000000-0000-4000-8000-000000000001';
const JSON_TYPE = 'application/json';

const rateLimited = new Counter('rate_limited');         // the overlay's limit override is missing
const gatewayFallback = new Counter('gateway_fallback'); // circuit breaker or its timeout tripped

function rampTo(peak, fn) {
  return {
    executor: 'ramping-arrival-rate',
    exec: fn,
    startRate: 1,
    timeUnit: '1s',
    preAllocatedVUs: Math.max(20, peak),
    maxVUs: peak * 4,
    stages: [
      { target: peak, duration: `${RAMP_SECONDS}s` },
      { target: peak, duration: `${HOLD_SECONDS}s` },
    ],
  };
}

const scenarios = {};
if (HCP_PEAK_RATE > 0) scenarios.hcp_events = rampTo(HCP_PEAK_RATE, 'createHcp');
if (MSR_PEAK_RATE > 0) scenarios.msr_events = rampTo(MSR_PEAK_RATE, 'createMsr');
if (PLAN_RATE > 0) {
  scenarios.visit_plans = {
    executor: 'constant-arrival-rate',
    exec: 'createVisitPlan',
    rate: PLAN_RATE,
    timeUnit: '1s',
    duration: `${RAMP_SECONDS + HOLD_SECONDS}s`,
    preAllocatedVUs: Math.max(20, PLAN_RATE),
    maxVUs: PLAN_RATE * 4,
  };
}
if (Object.keys(scenarios).length === 0) {
  throw new Error('Set at least one of PLAN_RATE, HCP_PEAK_RATE, MSR_PEAK_RATE');
}

export const options = {
  scenarios,
  setupTimeout: '10m',
  thresholds: {
    rate_limited: [{ threshold: 'count==0', abortOnFail: true }],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
  },
};

function login() {
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: __ENV.USERNAME,
    password: __ENV.PASSWORD,
  }), { headers: { 'Content-Type': JSON_TYPE } });
  if (res.status !== 200) throw new Error(`Login failed: HTTP ${res.status}`);
  return res.json('token');
}

function postJson(path, body, token) {
  const res = http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': JSON_TYPE },
  });
  if (res.status === 429) rateLimited.add(1);
  if (res.status === 503) gatewayFallback.add(1);
  return res;
}

function hcpBody(email) {
  return { name: 'Load', surname: 'Test', email, specialties: ['CARD'] };  // names allow letters only
}

// Fresh HCPs per run: a visit plan is unique per HCP, MSR and day, so reruns never collide.
export function setup() {
  const token = login();
  const claims = JSON.parse(encoding.b64decode(token.split('.')[1], 'rawurl', 's'));
  if (PLAN_RATE > 0 && !claims.msr_id) {
    throw new Error('PLAN_RATE needs a user with an msr_id claim: set IDENTITY_SEED_MSR_ID to an active MSR');
  }

  const runId = Date.now();
  const hcpIds = [];
  if (PLAN_RATE > 0) {
    for (let i = 0; i < HCP_COUNT; i++) {
      const res = postJson('/api/v1/healthcareprof/create', hcpBody(`seed-${runId}-${i}@example.test`), token);
      if (res.status !== 201) throw new Error(`Seeding HCP ${i} failed: HTTP ${res.status} ${res.body}`);
      hcpIds.push(res.json('id'));
    }
  }
  return { token, tokenAt: Date.now(), runId, hcpIds };
}

// One copy per VU, so a run longer than the token's life keeps working.
let vuToken;
let vuTokenAt;

function currentToken(data) {
  if (!vuToken) {
    vuToken = data.token;
    vuTokenAt = data.tokenAt;
  }
  if (Date.now() - vuTokenAt > TOKEN_MAX_AGE_MS) {
    vuToken = login();
    vuTokenAt = Date.now();
  }
  return vuToken;
}

export function createHcp(data) {
  const email = `hcp-${data.runId}-${exec.scenario.iterationInTest}@example.test`;
  const res = postJson('/api/v1/healthcareprof/create', hcpBody(email), currentToken(data));
  check(res, { 'HCP created': (r) => r.status === 201 });
}

export function createMsr(data) {
  const email = `msr-${data.runId}-${exec.scenario.iterationInTest}@example.test`;
  const res = postJson('/api/v1/medicalsalesrep/create', { name: 'Load', surname: 'Test', email }, currentToken(data));
  check(res, { 'MSR created': (r) => r.status === 201 });
}

export function createVisitPlan(data) {
  const i = exec.scenario.iterationInTest;
  const day = Math.floor(i / data.hcpIds.length) + 1;  // a later day once every HCP is used
  const res = postJson('/api/v1/visitplan/create', {
    visitDateTime: new Date(data.runId + day * DAY_MS).toISOString().slice(0, 19),
    healthCareProfId: data.hcpIds[i % data.hcpIds.length],
    visitSiteId: VISIT_SITE_ID,
  }, currentToken(data));
  check(res, { 'visit plan created': (r) => r.status === 201 });
}

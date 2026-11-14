// The gateway's per-client rate limit, switched ON. See load-test/README.md.
//
//   burst   one client sends more requests than its bucket holds: the excess must get a fast 429, never a 5xx
//   spoof   the same client, now limited, sends a different X-Forwarded-For on each request: it must stay limited
//
// Run it WITHOUT the load-test overlay, right after restarting api-gateway: the buckets live in its memory, and any
// earlier traffic from this machine has already used some of this client's.

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const CAPACITY = Number(__ENV.LIMIT_CAPACITY || 20);              // gateway.ratelimit.capacity
const REFILL_TOKENS = Number(__ENV.LIMIT_REFILL_TOKENS || 20);    // gateway.ratelimit.refill-tokens
const REFILL_SECONDS = Number(__ENV.LIMIT_REFILL_SECONDS || 60);  // gateway.ratelimit.refill-seconds
const BURST = Number(__ENV.BURST || CAPACITY * 3);
const SPOOF_REQUESTS = Number(__ENV.SPOOF_REQUESTS || 40);

const JSON_TYPE = 'application/json';
const PROBE = '/api/v1/products/list?name=zz&pageSize=1';         // a cheap authenticated read

const overLimit = new Counter('requests_over_limit');            // let through beyond the bucket's size
const serverErrors = new Counter('server_errors');               // 5xx: the limiter should answer 429 instead
const limited = new Counter('limited');
const limitedTime = new Trend('limited_response_ms', true);
const spoofBypass = new Counter('forwarded_for_bypass');         // let through only because of a forged header

export const options = {
  scenarios: {
    limit: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '2m' },
  },
  thresholds: {
    requests_over_limit: ['count==0'],
    server_errors: ['count==0'],
    limited: ['count>0'],
    limited_response_ms: ['p(95)<100'],
    forwarded_for_bypass: ['count==0'],
  },
};

export function setup() {
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: __ENV.USERNAME,
    password: __ENV.PASSWORD,
  }), { headers: { 'Content-Type': JSON_TYPE } });
  if (res.status === 429) throw new Error('Already rate limited: restart api-gateway and run again');
  if (res.status !== 200) throw new Error(`Login failed: HTTP ${res.status}`);
  return { token: res.json('token') };
}

function probe(token, phase, forwardedFor) {
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': JSON_TYPE };
  if (forwardedFor) headers['X-Forwarded-For'] = forwardedFor;
  const res = http.post(`${BASE_URL}${PROBE}`, null, { headers, tags: { phase } });
  if (res.status >= 500) serverErrors.add(1, { phase });
  if (res.status === 429) {
    limited.add(1, { phase });
    limitedTime.add(res.timings.duration, { phase });
    check(res, { '429 says when to retry': (r) => Boolean(r.headers['X-Ratelimit-Retry-After']) });
  }
  return res;
}

export default function (data) {
  // Burst: the login in setup() already took one token from this client's bucket.
  const startedAt = Date.now();
  let allowed = 1;
  for (let i = 0; i < BURST; i++) {
    if (probe(data.token, 'burst').status !== 429) allowed++;
  }
  const elapsedSeconds = (Date.now() - startedAt) / 1000;
  const refilled = Math.ceil(elapsedSeconds * REFILL_TOKENS / REFILL_SECONDS);
  if (allowed > CAPACITY + refilled) overLimit.add(allowed - CAPACITY - refilled);
  console.log(`burst: ${allowed} of ${BURST + 1} requests let through in ${elapsedSeconds.toFixed(1)}s `
    + `(bucket ${CAPACITY}, about ${refilled} refilled)`);

  // Spoof: the bucket is empty now, so beyond what refills meanwhile, anything let through here got past it with
  // the forged header alone.
  const spoofStartedAt = Date.now();
  let bypassed = 0;
  for (let i = 0; i < SPOOF_REQUESTS; i++) {
    const forged = `198.51.100.${(i % 254) + 1}`;                 // TEST-NET-2: never a real client
    if (probe(data.token, 'spoof', forged).status !== 429) bypassed++;
  }
  const spoofRefilled = Math.ceil((Date.now() - spoofStartedAt) / 1000 * REFILL_TOKENS / REFILL_SECONDS);
  if (bypassed > spoofRefilled) spoofBypass.add(bypassed - spoofRefilled);
  console.log(`spoof: ${bypassed} of ${SPOOF_REQUESTS} requests with a forged X-Forwarded-For let through`);
}

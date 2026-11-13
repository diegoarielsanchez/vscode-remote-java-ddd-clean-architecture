# Load tests: RabbitMQ and Redis

Stress and failure tests for the event flow and the cache used by the MSR, HCP and Visit services.

```
MSR/HCP write → outbox table → relay → msr.events / hcp.events → Visit (and Settlement, MSR only)
                                                                   ├ updates its snapshot
                                                                   └ evicts the Redis key
Visit plan or visit create → Redis (60s TTL) → on a miss: HTTP active-status + snapshot write
```

**Use a disposable environment.** These tests remove the gateway rate limit, write audit rows that are meant to be
permanent, and flush Redis. Never point them at data you care about.

## Set up

1. Copy `.env.example` to `.env`. Use the compose stack as it is, on the `prod` profile: under `dev`, MSR and HCP
   publish no events.
2. Create an MSR and set `IDENTITY_SEED_MSR_ID` to its ID. Visit plans need a token with an `msr_id` claim.
3. Start the stack with the overlay, and load the variables the commands below use:

   ```bash
   docker compose -f docker-compose.yml -f load-test/docker-compose.loadtest.yml up -d
   set -a; . ./.env; set +a
   ```

4. Import dashboards 10991 (RabbitMQ) and 763 (Redis) into Grafana at <http://localhost:3000>.
5. Install [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/). Use a second machine if you can, because k6
   competes with the stack for CPU on the same host.

## What to watch

| Signal | Query | Healthy under load |
|---|---|---|
| Queue lag | `rabbitmq_queue_messages_ready{queue=~"visit-service.*v2.*\|settlement-service.msr.queue"}` | Returns to 0 after the load |
| Dead letters | `rabbitmq_queue_messages_ready{queue=~".*dlq"}` | Stays at 0 |
| Broker alarm | `rabbitmq_alarms_memory_used_watermark` | Stays at 0 |
| Redis hit ratio | `rate(redis_keyspace_hits_total[1m]) / (rate(redis_keyspace_hits_total[1m]) + rate(redis_keyspace_misses_total[1m]))` | High and stable |
| Redis evictions | `rate(redis_evicted_keys_total[1m])` | 0 |
| Real cache misses | `rate(http_server_request_duration_seconds_count{http_route=~".*active-status"}[1m])` | Far below the create rate |
| API latency | `histogram_quantile(0.95, sum by (le, exported_job) (rate(http_server_request_duration_seconds_bucket[1m])))` | Within your target |

The two `http_server_*` names depend on the OTel agent version, so check them in the Prometheus UI first. Redis
counters cover Visit, Order and Catalog together, so send only Visit traffic when measuring Visit's hit ratio.

The outbox backlog has no metric. Run this against the HCP and MSR databases during every test:

```sql
SELECT count(*) AS pending, now() - min(occurred_at) AS oldest
FROM outbox_event WHERE published_at IS NULL;
```

## Tests

Run k6 with the seed user's credentials plus the rates each test sets:

```bash
USERNAME=user PASSWORD='...' PLAN_RATE=50 k6 run load-test/k6/events-and-cache.js
```

| Variable | Default | Meaning |
|---|---|---|
| `PLAN_RATE` | 0 | Visit plan creates per second (exercises Redis) |
| `HCP_PEAK_RATE` | 0 | Peak HCP creates per second (exercises `hcp.events`) |
| `MSR_PEAK_RATE` | 0 | Peak MSR creates per second (exercises `msr.events`) |
| `HCP_COUNT` | 100 | HCPs seeded per run, which is the number of HCP cache keys |
| `RAMP_SECONDS` / `HOLD_SECONDS` | 300 / 120 | Ramp and hold time |
| `BASE_URL` | `http://localhost:8080` | Gateway URL |

k6 stops at the first 429, because it means the overlay isn't applied. The `gateway_fallback` count in its summary is
how many requests got the gateway's 503 fallback response.

Run the tests in this order. The "Expect" column is a prediction from the code, not a measurement.

| # | Test | How | Expect |
|---|---|---|---|
| 1 | Raw limits | Commands below | A ceiling to compare the app against |
| 2 | Cache baseline | `PLAN_RATE=50` | High hit ratio; about `2 × active keys / 60` real misses per second at most |
| 3 | Outbox limit | `HCP_PEAK_RATE=200` | Backlog grows above roughly 100 events/s (50 per run, about 1 run/s, 2 replicas) |
| 4 | Consumer catch-up | `docker compose stop visit-service`, run `HCP_PEAK_RATE=50`, then start it | Queue drains and the DLQ stays empty; record the drain rate |
| 5 | Full queue | Policy below, with `visit-service` stopped and `MSR_PEAK_RATE=50` | Broker refuses publishes, MSR's relay stalls, and Settlement stops getting MSR events too |
| 6 | Broker outage | `docker stop rabbitmq-ddd-clean` under load, then `docker start` | Writes still succeed and the backlog grows; listeners can take up to 60s to reconnect |
| 7 | Cache stampede | `docker exec redis-ddd-clean redis-cli -a "$REDIS_PASSWORD" FLUSHALL` under `PLAN_RATE=50` | Brief spike in `active-status` calls and snapshot writes |
| 8 | Redis outage | `docker stop redis-ddd-clean`; separately `docker pause` and `docker unpause` | Should fall back to HTTP, but `spring.data.redis.timeout` is unset, so calls may stall and return gateway 503s |
| 9 | Data freshness | Deactivate an HCP as admin, then retry a visit plan for it until rejected | About 1–2s; up to 60s if events are stuck |
| 10 | Soak | `PLAN_RATE=20 HCP_PEAK_RATE=20 RAMP_SECONDS=60 HOLD_SECONDS=14400` | Flat memory and lag, no gradual slowdown |

### Test 1: raw limits

```bash
docker run --rm --network ddd-clean-net pivotalrabbitmq/perf-test \
  --uri "amqp://$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD@rabbitmq-ddd-clean:5672" \
  --quorum-queue --queue perf-test -x 1 -y 2 -f persistent -c 50 -s 1000 -z 120
docker exec rabbitmq-ddd-clean rabbitmqctl delete_queue perf-test

docker exec redis-ddd-clean redis-benchmark -a "$REDIS_PASSWORD" -q -t get,set -n 100000
```

### Test 5: full queue

Reaching the real limit of 100,000 messages takes too long, so cap the queue with a policy. Always clear it afterwards.

```bash
docker exec rabbitmq-ddd-clean rabbitmqctl set_policy --apply-to queues cap \
  '^visit-service\.msr\.v2\.queue$' '{"max-length":1000,"overflow":"reject-publish"}'
# ...run the test...
docker exec rabbitmq-ddd-clean rabbitmqctl clear_policy cap
```

### Check that no events were lost

After tests 4, 5 and 6, once the backlog and queues are empty, compare these counts with the ones taken before the
test. Rows published by HCP and MSR together should match the rows Visit processed.

```sql
-- HCP and MSR databases
SELECT count(*) FROM outbox_event WHERE published_at IS NOT NULL;
-- Visit database
SELECT count(*) FROM processed_event;
```

## Reading the results

All services share one host here, so use the numbers to compare runs and find bottlenecks, not to predict production
capacity. Change one thing at a time, such as `msr.outbox.relay.batch-size`, `visit.events.prefetch` or the replica
count, and rerun the same test.

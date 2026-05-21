#!/bin/bash
# Starts all microservices in the correct order:
# 1. Eureka Server  (waits until healthy before continuing)
# 2. API Gateway    (waits until healthy before continuing)
# 3. All microservices in parallel

set -e

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$REPO_ROOT/logs"
mkdir -p "$LOG_DIR"

# ---- helpers ----------------------------------------------------------------

wait_for_http() {
  local name="$1"
  local url="$2"
  local max_wait="${3:-120}"   # seconds
  local elapsed=0

  echo "  Waiting for $name at $url ..."
  until curl -sf "$url" > /dev/null 2>&1; do
    sleep 3
    elapsed=$((elapsed + 3))
    if [ "$elapsed" -ge "$max_wait" ]; then
      echo "  ERROR: $name did not become healthy within ${max_wait}s. Check $LOG_DIR/$name.log"
      exit 1
    fi
    echo "  ... still waiting for $name ($elapsed/${max_wait}s)"
  done
  echo "  $name is UP."
}

start_service() {
  local name="$1"
  local pl="$2"
  local extra_env="${3:-}"
  echo "Starting $name ..."
  env $extra_env mvn -pl "$pl" -am spring-boot:run \
      --no-transfer-progress \
      > "$LOG_DIR/$name.log" 2>&1 &
  echo "  PID $! → $LOG_DIR/$name.log"
}

# ---- startup sequence -------------------------------------------------------

echo "============================================================"
echo "  Starting all microservices"
echo "============================================================"

# 1. Eureka Server
start_service "eureka-server" "eureka-server"
wait_for_http  "eureka-server" "http://localhost:8761/actuator/health" 120

# 2. API Gateway
start_service "api-gateway" "api-gateway"
wait_for_http  "api-gateway" "http://localhost:8080/actuator/health" 90

# 3. Microservices (parallel)
start_service "identity-application"  "identity-service/identity-application"
start_service "msr-application"       "medical-sales-rep-service/msr-application"
start_service "hcp-application"       "healthcare-prof-service/hcp-application"
start_service "visit-application"     "visit-service/visit-application" "DB_PASSWORD=Riverplate1!"
start_service "settlement-application" "settlement-service/settlement-application"

echo ""
echo "============================================================"
echo "  All services launched. Tail logs in: $LOG_DIR/"
echo "  Press Ctrl+C to stop this script (services keep running)."
echo "  To stop a service: kill <PID> or pkill -f spring-boot:run"
echo "============================================================"

# Keep script alive so Ctrl+C gives a clean exit message
wait

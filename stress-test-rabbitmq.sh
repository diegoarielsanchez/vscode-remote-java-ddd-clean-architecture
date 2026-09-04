#!/usr/bin/env bash
# =============================================================================
# stress-test-rabbitmq.sh
#
# Focused RabbitMQ stress test:
#   1. Create & activate 5 MedicalSalesRep  (MSR service :8086)
#   2. Create & activate 5 HealthCareProf   (HCP service :8087)
#   3. Fire all 10 deactivate calls CONCURRENTLY
#        → 5x RabbitMQ  msr.events / msr.deactivated
#        → 5x RabbitMQ  hcp.events / hcp.deactivated
#
# Unlike simulate-full-workflow.sh (which builds a full 20/200/200 dataset
# and deactivates sequentially), this script only touches the 10 entities it
# needs and deactivates them all at once, in parallel, to put a burst of
# concurrent publishes through RabbitMQ.
#
# Prerequisites (run on the machine where the stack actually lives):
#   docker compose up -d          # RabbitMQ + Postgres + all services
#   ./start-all-services.sh       # if not running the stack via Docker
#   Observe results: http://localhost:15672 (guest/guest)
# =============================================================================

set -uo pipefail

# ── Service base URLs ─────────────────────────────────────────────────────────
IDENTITY_URL="${IDENTITY_URL:-http://localhost:8090}"
MSR_URL="${MSR_URL:-http://localhost:8086}"
HCP_URL="${HCP_URL:-http://localhost:8087}"

# ── Terminal colours ──────────────────────────────────────────────────────────
GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'
RED='\033[0;31m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}   $*"; }
section() { echo -e "\n${BOLD}${CYAN}$*${NC}"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}   $*"; }
error()   { echo -e "${RED}[ERROR]${NC}  $*"; }

MSR_FIRST=("Adrian" "Brandon" "Cedric" "Damian" "Elliot")
MSR_LAST=("Alcott" "Barlow" "Clifton" "Dalton" "Easton")

HCP_FIRST=("Ana" "Beatriz" "Carmen" "Diana" "Elena")
HCP_LAST=("Aguilar" "Blanco" "Castillo" "Delgado" "Espinoza")
SPECIALTIES=("CARD" "DERM" "NEUR" "PED" "ORTH")

MSR_IDS=()
HCP_IDS=()
STAMP=$(date +%s)

# =============================================================================
# STEP 0 — Authenticate
# =============================================================================
section "════ STEP 0 · Authentication ════"

TOKEN=$(curl -s -X POST "$IDENTITY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"Apatehia65$"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "$TOKEN" ]]; then
  error "Could not obtain JWT. Is identity-service running on $IDENTITY_URL?"
  exit 1
fi
info "JWT obtained: ${TOKEN:0:40}..."

# =============================================================================
# STEP 1 — Create & activate 5 MedicalSalesRep
# =============================================================================
section "════ STEP 1 · Create & activate 5 MedicalSalesReps ════"

for i in $(seq 0 4); do
  FIRST="${MSR_FIRST[$i]}"
  LAST="${MSR_LAST[$i]}"
  EMAIL="${FIRST,,}.${LAST,,}.${STAMP}@msrstress.com"

  RESP=$(curl -s -X POST "$MSR_URL/api/v1/medicalsalesrep/create" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"name\":\"$FIRST\",\"surname\":\"$LAST\",\"email\":\"$EMAIL\"}")

  ID=$(echo "$RESP" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
  if [[ -z "$ID" ]]; then
    error "Create MSR [$((i+1))/5] FAILED — $RESP"
    exit 1
  fi
  MSR_IDS+=("$ID")
  info "MSR [$((i+1))/5] $FIRST $LAST → $ID"

  HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$MSR_URL/api/v1/medicalsalesrep/$ID/activate" \
    -H "Authorization: Bearer $TOKEN")
  [[ "$HTTP" == "200" ]] && info "  activated $ID" || warn "  activate HTTP $HTTP for $ID"
done

# =============================================================================
# STEP 2 — Create & activate 5 HealthCareProf
# =============================================================================
section "════ STEP 2 · Create & activate 5 HealthCareProfs ════"

for i in $(seq 0 4); do
  FIRST="${HCP_FIRST[$i]}"
  LAST="${HCP_LAST[$i]}"
  SPECIALTY="${SPECIALTIES[$i]}"
  EMAIL="${FIRST,,}.${LAST,,}.${STAMP}@hcpstress.com"

  RESP=$(curl -s -X POST "$HCP_URL/api/v1/healthcareprof/create" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"name\":\"$FIRST\",\"surname\":\"$LAST\",\"email\":\"$EMAIL\",\"specialties\":[\"$SPECIALTY\"]}")

  ID=$(echo "$RESP" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
  if [[ -z "$ID" ]]; then
    error "Create HCP [$((i+1))/5] FAILED — $RESP"
    exit 1
  fi
  HCP_IDS+=("$ID")
  info "HCP [$((i+1))/5] $FIRST $LAST ($SPECIALTY) → $ID"

  HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$HCP_URL/api/v1/healthcareprof/$ID/activate" \
    -H "Authorization: Bearer $TOKEN")
  [[ "$HTTP" == "200" ]] && info "  activated $ID" || warn "  activate HTTP $HTTP for $ID"
done

# =============================================================================
# STEP 3 — Deactivate all 10 CONCURRENTLY → burst of RabbitMQ publishes
# =============================================================================
section "════ STEP 3 · Concurrent deactivation burst (5 MSR + 5 HCP) ════"
info "Firing all 10 deactivate calls in parallel..."

RESULT_DIR=$(mktemp -d)

for i in "${!MSR_IDS[@]}"; do
  ID="${MSR_IDS[$i]}"
  (
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "$MSR_URL/api/v1/medicalsalesrep/$ID/deactivate" \
      -H "Authorization: Bearer $TOKEN")
    echo "MSR ${MSR_FIRST[$i]} ${MSR_LAST[$i]} ($ID) HTTP $HTTP" > "$RESULT_DIR/msr_$i"
  ) &
done

for i in "${!HCP_IDS[@]}"; do
  ID="${HCP_IDS[$i]}"
  (
    HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "$HCP_URL/api/v1/healthcareprof/$ID/deactivate" \
      -H "Authorization: Bearer $TOKEN")
    echo "HCP ${HCP_FIRST[$i]} ${HCP_LAST[$i]} ($ID) HTTP $HTTP" > "$RESULT_DIR/hcp_$i"
  ) &
done

wait
info "All 10 deactivate calls returned."

for f in "$RESULT_DIR"/msr_* "$RESULT_DIR"/hcp_*; do
  [[ -f "$f" ]] && info "  $(cat "$f")"
done
rm -rf "$RESULT_DIR"

# =============================================================================
# Summary
# =============================================================================
section "════ STRESS TEST COMPLETE ════"
echo -e "${BOLD}"
echo "  MSRs deactivated: ${#MSR_IDS[@]}  →  exchange msr.events  key msr.deactivated"
echo "  HCPs deactivated: ${#HCP_IDS[@]}  →  exchange hcp.events  key hcp.deactivated"
echo -e "${NC}"
echo "  RabbitMQ Management UI : http://localhost:15672  (guest / guest)"
echo ""
echo "  What to check in RabbitMQ:"
echo "    Exchanges → msr.events  → message rate spike on routing key msr.deactivated"
echo "    Exchanges → hcp.events  → message rate spike on routing key hcp.deactivated"
echo "    Queues    → visit-service.msr.queue  → messages queued/consumed"
echo "    Queues    → visit-service.hcp.queue  → messages queued/consumed"
echo ""

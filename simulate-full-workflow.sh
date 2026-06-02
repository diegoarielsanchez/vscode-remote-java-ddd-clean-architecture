#!/usr/bin/env bash
# =============================================================================
# simulate-full-workflow.sh
#
# Full end-to-end simulation:
#   1. Create & activate 20 MedicalSalesRep  (MSR service  :8086)
#   2. Create & activate 200 HealthCareProf  (HCP service  :8087)
#   3. Create 10 VisitPlans per MSR          (Visit service:8088)
#      → each MSR visits 10 different HCPs (200 VisitPlans total)
#      → dates fall in the next calendar month
#   4. Deactivate 5 MSRs that have VisitPlans  → RabbitMQ msr.deactivated
#   5. Deactivate 5 HCPs that have VisitPlans  → RabbitMQ hcp.deactivated
#
# Prerequisites:
#   All five services running + RabbitMQ running
#   Observe results: http://localhost:15672 (guest/guest)
# =============================================================================

set -uo pipefail

# ── Service base URLs ─────────────────────────────────────────────────────────
IDENTITY_URL="http://localhost:8090"
MSR_URL="http://localhost:8086"
HCP_URL="http://localhost:8087"
VISIT_URL="http://localhost:8088"

# ── Terminal colours ──────────────────────────────────────────────────────────
GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'
RED='\033[0;31m'; BOLD='\033[1m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}   $*"; }
section() { echo -e "\n${BOLD}${CYAN}$*${NC}"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}   $*"; }
error()   { echo -e "${RED}[ERROR]${NC}  $*"; }

# ── Name pools (letters only — domain regex ^[A-Za-z\s]+$) ───────────────────

# 20 MSR first names + 20 surnames (one-to-one pairing)
MSR_FIRST=(
  "Adrian"   "Brandon"  "Cedric"   "Damian"   "Elliot"
  "Fabian"   "Gerald"   "Herbert"  "Ivan"     "Julian"
  "Kenneth"  "Leonard"  "Marcus"   "Nathan"   "Oliver"
  "Patrick"  "Quinton"  "Roderick" "Sebastian" "Tristan"
)
MSR_LAST=(
  "Alcott"   "Barlow"   "Clifton"  "Dalton"   "Easton"
  "Fulton"   "Grover"   "Halton"   "Irving"   "Jarvis"
  "Kelton"   "Lawton"   "Morton"   "Norton"   "Overton"
  "Paxton"   "Quincy"   "Ralston"  "Sutton"   "Tilton"
)

# 20 HCP first names × 10 HCP last names = 200 unique HCPs
HCP_FIRST=(
  "Ana"     "Beatriz" "Carmen"  "Diana"   "Elena"
  "Fatima"  "Gloria"  "Helena"  "Ingrid"  "Julia"
  "Karla"   "Lorena"  "Monica"  "Natalia" "Olivia"
  "Paula"   "Quenia"  "Rosa"    "Sofia"   "Teresa"
)
HCP_LAST=(
  "Aguilar"  "Blanco"   "Castillo" "Delgado"  "Espinoza"
  "Fuentes"  "Guerrero" "Herrera"  "Ibarra"   "Jimenez"
)

# 10 specialty codes (rotated across HCPs)
SPECIALTIES=("CARD" "DERM" "NEUR" "PED" "ORTH" "ONCO" "PSYC" "ODON" "OPHT" "ENT")

# 10 visit times used for the 10 visits per MSR
VISIT_TIMES=(
  "09:00:00" "10:00:00" "11:00:00" "14:00:00" "15:00:00"
  "09:30:00" "10:30:00" "11:30:00" "14:30:00" "15:30:00"
)

# Next-month base (YYYY-MM) for VisitPlan dates
NEXT_MONTH=$(date -d "$(date +%Y-%m-01) +1 month" +%Y-%m)

# ── Runtime state ─────────────────────────────────────────────────────────────
MSR_IDS=()
HCP_IDS=()
VISIT_PLAN_IDS=()
FAIL_MSR=0; FAIL_HCP=0; FAIL_VP=0

# =============================================================================
# STEP 0 — Authenticate
# =============================================================================
section "════ STEP 0 · Authentication ════"

TOKEN=$(curl -s -X POST "$IDENTITY_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"Apatehia65$"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [[ -z "$TOKEN" ]]; then
  error "Could not obtain JWT. Is identity-service running on port 8090?"
  exit 1
fi
info "JWT obtained: ${TOKEN:0:40}..."

# =============================================================================
# STEP 1 — Create & activate 20 MedicalSalesRep
# =============================================================================
section "════ STEP 1 · Create 20 MedicalSalesReps ════"

for i in $(seq 0 19); do
  FIRST="${MSR_FIRST[$i]}"
  LAST="${MSR_LAST[$i]}"
  EMAIL="${FIRST,,}.${LAST,,}@msrsim.com"

  RESP=$(curl -s -X POST "$MSR_URL/api/v1/medicalsalesrep/create" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"name\":\"$FIRST\",\"surname\":\"$LAST\",\"email\":\"$EMAIL\"}")

  ID=$(echo "$RESP" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
  if [[ -z "$ID" ]]; then
    error "Create MSR [$((i+1))/20] FAILED — $RESP"
    FAIL_MSR=$((FAIL_MSR+1))
  else
    MSR_IDS+=("$ID")
    info "MSR [$((i+1))/20] $FIRST $LAST → $ID"
  fi
done

section "── Activating ${#MSR_IDS[@]} MSRs ──"
for ID in "${MSR_IDS[@]}"; do
  HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$MSR_URL/api/v1/medicalsalesrep/activate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"medicalSalesRepId\":\"$ID\"}")
  [[ "$HTTP" == "200" ]] && info "  activated $ID" || warn "  HTTP $HTTP for $ID"
done

# =============================================================================
# STEP 2 — Create & activate 200 HealthCareProf
#          Layout: for each of 10 last-names, iterate 20 first-names
#          → 10 × 20 = 200 unique HCPs
# =============================================================================
section "════ STEP 2 · Create 200 HealthCareProfs ════"

IDX=0
for LAST in "${HCP_LAST[@]}"; do
  for FIRST in "${HCP_FIRST[@]}"; do
    SPECIALTY="${SPECIALTIES[$((IDX % 10))]}"
    EMAIL="${FIRST,,}.${LAST,,}.${IDX}@hcpsim.com"

    RESP=$(curl -s -X POST "$HCP_URL/api/v1/healthcareprof/create" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d "{\"name\":\"$FIRST\",\"surname\":\"$LAST\",\"email\":\"$EMAIL\",\"specialties\":[\"$SPECIALTY\"]}")

    ID=$(echo "$RESP" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    if [[ -z "$ID" ]]; then
      error "Create HCP [$((IDX+1))/200] FAILED — $RESP"
      FAIL_HCP=$((FAIL_HCP+1))
    else
      HCP_IDS+=("$ID")
      info "HCP [$((IDX+1))/200] $FIRST $LAST ($SPECIALTY) → $ID"
    fi
    IDX=$((IDX+1))
  done
done

section "── Activating ${#HCP_IDS[@]} HCPs (parallel) ──"
for ID in "${HCP_IDS[@]}"; do
  curl -s -o /dev/null \
    -X POST "$HCP_URL/api/v1/healthcareprof/activate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"id\":\"$ID\"}" &
done
wait
info "All HCPs activated."

# =============================================================================
# STEP 3 — Create VisitPlans: 10 per MSR (200 total)
#          MSR[i] visits HCP[i*10 .. i*10+9]
#          Dates: next month, day 01-10, rotating times
# =============================================================================
section "════ STEP 3 · Create 200 VisitPlans (10 per MSR) ════"
info "Visit month: $NEXT_MONTH"

for MSR_IDX in $(seq 0 19); do
  MSR_ID="${MSR_IDS[$MSR_IDX]:-}"
  if [[ -z "$MSR_ID" ]]; then
    warn "Skipping MSR[$MSR_IDX] — no ID (create failed)"
    continue
  fi

  for V in $(seq 0 9); do
    HCP_IDX=$(( MSR_IDX * 10 + V ))
    HCP_ID="${HCP_IDS[$HCP_IDX]:-}"
    if [[ -z "$HCP_ID" ]]; then
      warn "Skipping HCP[$HCP_IDX] — no ID (create failed)"
      continue
    fi

    DAY=$(printf "%02d" $((V + 1)))
    TIME="${VISIT_TIMES[$V]}"
    VISIT_DT="${NEXT_MONTH}-${DAY}T${TIME}"
    SITE_ID=$(cat /proc/sys/kernel/random/uuid)
    COMMENT="Simulation: MSR ${MSR_FIRST[$MSR_IDX]} ${MSR_LAST[$MSR_IDX]} visits HCP $HCP_IDX"

    RESP=$(curl -s -X POST "$VISIT_URL/api/v1/visitplan/create" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d "{
        \"visitDateTime\":\"$VISIT_DT\",
        \"healthCareProfId\":\"$HCP_ID\",
        \"visitComments\":\"$COMMENT\",
        \"visitSiteId\":\"$SITE_ID\",
        \"medicalSalesRepId\":\"$MSR_ID\"
      }")

    VP_ID=$(echo "$RESP" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
    if [[ -z "$VP_ID" ]]; then
      error "VisitPlan FAILED [MSR $((MSR_IDX+1)), visit $((V+1))]: $RESP"
      FAIL_VP=$((FAIL_VP+1))
    else
      VISIT_PLAN_IDS+=("$VP_ID")
      info "VisitPlan [MSR $((MSR_IDX+1))/20, visit $((V+1))/10]: $VP_ID  $VISIT_DT"
    fi
  done
done

# =============================================================================
# STEP 4 — Deactivate 5 MSRs (those with VisitPlans → triggers RabbitMQ)
#          MSR[0..4] each own visit plans against HCP[0..49]
# =============================================================================
section "════ STEP 4 · Deactivate 5 MSRs  →  RabbitMQ: msr.events / msr.deactivated ════"

for i in $(seq 0 4); do
  ID="${MSR_IDS[$i]:-}"
  [[ -z "$ID" ]] && warn "MSR[$i] has no ID, skipping" && continue

  HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$MSR_URL/api/v1/medicalsalesrep/deactivate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"medicalSalesRepId\":\"$ID\"}")

  info "MSR deactivated [$((i+1))/5]: ${MSR_FIRST[$i]} ${MSR_LAST[$i]} ($ID)  HTTP $HTTP"
done

# =============================================================================
# STEP 5 — Deactivate 5 HCPs (those with VisitPlans → triggers RabbitMQ)
#          HCP[0..4] appear in MSR[0]'s visit plan
# =============================================================================
section "════ STEP 5 · Deactivate 5 HCPs  →  RabbitMQ: hcp.events / hcp.deactivated ════"

for i in $(seq 0 4); do
  ID="${HCP_IDS[$i]:-}"
  [[ -z "$ID" ]] && warn "HCP[$i] has no ID, skipping" && continue

  HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "$HCP_URL/api/v1/healthcareprof/deactivate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"id\":\"$ID\"}")

  info "HCP deactivated [$((i+1))/5]: $ID  HTTP $HTTP"
done

# =============================================================================
# Summary
# =============================================================================
section "════ SIMULATION COMPLETE ════"
echo -e "${BOLD}"
echo "  MSRs   created  : ${#MSR_IDS[@]} / 20    (failures: $FAIL_MSR)"
echo "  HCPs   created  : ${#HCP_IDS[@]} / 200   (failures: $FAIL_HCP)"
echo "  VisitPlans      : ${#VISIT_PLAN_IDS[@]} / 200   (failures: $FAIL_VP)"
echo "  MSRs deactivated: 5  →  exchange msr.events  key msr.deactivated"
echo "  HCPs deactivated: 5  →  exchange hcp.events  key hcp.deactivated"
echo -e "${NC}"
echo "  RabbitMQ Management UI : http://localhost:15672  (guest / guest)"
echo ""
echo "  What to check in RabbitMQ:"
echo "    Exchanges → msr.events  → message rate spike on routing key msr.deactivated"
echo "    Exchanges → hcp.events  → message rate spike on routing key hcp.deactivated"
echo "    Queues    → visit-service.msr.queue  → 5 messages queued"
echo "    Queues    → visit-service.hcp.queue  → 5 messages queued"
echo ""

#!/usr/bin/env bash
# Builds the visit-service image with the build context Jenkins uses: the
# service's own directory, not the repo root. visit-infra reads across
# services (its anti-corruption layer), so this needs three named build
# contexts: domain-commons, plus the medical-sales-rep-service and
# healthcare-prof-service source trees for msr-domain/hcp-domain (see
# visit-application/Dockerfile).
#
# Usage: ./visit-service/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    --build-context domain-commons=domain-commons \
    --build-context medical-sales-rep-service=medical-sales-rep-service \
    --build-context healthcare-prof-service=healthcare-prof-service \
    -f visit-service/visit-application/Dockerfile \
    -t "visit-service:${TAG}" \
    visit-service

echo "Built visit-service:${TAG}"

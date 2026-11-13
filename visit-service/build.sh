#!/usr/bin/env bash
# Builds the visit-service image with the build context Jenkins uses: the
# service's own directory, not the repo root, plus domain-commons as a named
# build context (see visit-application/Dockerfile).
#
# Usage: ./visit-service/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    --build-context domain-commons=domain-commons \
    -f visit-service/visit-application/Dockerfile \
    -t "visit-service:${TAG}" \
    visit-service

echo "Built visit-service:${TAG}"

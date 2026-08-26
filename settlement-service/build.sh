#!/usr/bin/env bash
# Builds the settlement-service image with the build context Jenkins uses:
# the service's own directory, not the repo root. domain-commons lives
# outside this context, so it is supplied as a separate named build context
# (see settlement-application/Dockerfile).
#
# Usage: ./settlement-service/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    --build-context domain-commons=domain-commons \
    -f settlement-service/settlement-application/Dockerfile \
    -t "settlement-service:${TAG}" \
    settlement-service

echo "Built settlement-service:${TAG}"

#!/usr/bin/env bash
# Builds the healthcare-prof-service image with the build context Jenkins
# uses: the service's own directory, not the repo root. domain-commons lives
# outside this context, so it is supplied as a separate named build context
# (see hcp-application/Dockerfile).
#
# Usage: ./healthcare-prof-service/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    --build-context domain-commons=domain-commons \
    -f healthcare-prof-service/hcp-application/Dockerfile \
    -t "healthcare-prof-service:${TAG}" \
    healthcare-prof-service

echo "Built healthcare-prof-service:${TAG}"

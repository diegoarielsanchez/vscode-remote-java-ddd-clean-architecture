#!/usr/bin/env bash
# Builds the medical-sales-rep-service image with the build context Jenkins
# uses: the service's own directory, not the repo root. domain-commons lives
# outside this context, so it is supplied as a separate named build context
# (see msr-application/Dockerfile).
#
# Usage: ./medical-sales-rep-service/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    --build-context domain-commons=domain-commons \
    -f medical-sales-rep-service/msr-application/Dockerfile \
    -t "medical-sales-rep-service:${TAG}" \
    medical-sales-rep-service

echo "Built medical-sales-rep-service:${TAG}"

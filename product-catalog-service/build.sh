#!/usr/bin/env bash
# Builds the product-catalog-service image with the build context Jenkins
# uses: the service's own directory, not the repo root. domain-commons lives
# outside this context, so it is supplied as a separate named build context
# (see catalog-application/Dockerfile).
#
# Usage: ./product-catalog-service/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    --build-context domain-commons=domain-commons \
    -f product-catalog-service/catalog-application/Dockerfile \
    -t "product-catalog-service:${TAG}" \
    product-catalog-service

echo "Built product-catalog-service:${TAG}"

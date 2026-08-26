#!/usr/bin/env bash
# Builds the api-gateway image with the build context Jenkins uses: the
# service's own directory, not the repo root. `docker build ... .` from the
# repo root builds the monorepo's aggregator pom instead and fails at the
# final COPY (see Dockerfile's header comment).
#
# Usage: ./api-gateway/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    -f api-gateway/Dockerfile \
    -t "api-gateway:${TAG}" \
    api-gateway

echo "Built api-gateway:${TAG}"

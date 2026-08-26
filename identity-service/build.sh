#!/usr/bin/env bash
# Builds the identity-service image with the build context Jenkins uses: the
# service's own directory, not the repo root. identity-service does not depend
# on domain-commons, so no --build-context flag is needed (unlike its four
# sibling DDD services).
#
# Usage: ./identity-service/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    -f identity-service/identity-application/Dockerfile \
    -t "identity-service:${TAG}" \
    identity-service

echo "Built identity-service:${TAG}"

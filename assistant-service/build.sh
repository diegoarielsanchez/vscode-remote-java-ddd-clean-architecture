#!/usr/bin/env bash
# Builds the assistant-service image with the build context Jenkins uses: the
# service's own directory, not the repo root. domain-commons lives outside
# this context, so it is supplied as a separate named build context (see
# assistant-application/Dockerfile).
#
# Usage: ./assistant-service/build.sh [tag]   (default tag: local)
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

TAG="${1:-local}"
DOCKER_BUILDKIT=1 docker build \
    --build-context domain-commons=domain-commons \
    -f assistant-service/assistant-application/Dockerfile \
    -t "assistant-service:${TAG}" \
    assistant-service

echo "Built assistant-service:${TAG}"

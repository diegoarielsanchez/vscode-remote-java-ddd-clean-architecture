# Stages (BuildKit builds only what the target needs):
#   deps → check → check-report     typecheck, unit tests, npm audit   (Jenkins)
#   smoke → smoke-report            production build in headless Chromium (Jenkins)
#   deps → build → runtime          the image (default target; what compose builds)
# The *-report stages hold logs, JUnit XML and exit codes: a failing gate still
# delivers its report, and the Jenkinsfile decides from the exit code.

ARG NODE_IMAGE=node:22-alpine
# Must match the "playwright" version in package-lock.json (its browsers are in this image).
ARG PLAYWRIGHT_IMAGE=mcr.microsoft.com/playwright:v1.65.0-noble

# ── Dependencies ──────────────────────────────────────────────────────────────
FROM ${NODE_IMAGE} AS deps
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY . .

# ── Gates: typecheck, unit tests, dependency audit ────────────────────────────
FROM deps AS check
RUN mkdir -p /reports; \
    npm run typecheck > /reports/typecheck.log 2>&1; echo $? > /reports/typecheck.status; \
    node --conditions=react-server --test \
        --test-reporter=spec --test-reporter-destination=/reports/unit.log \
        --test-reporter=junit --test-reporter-destination=/reports/unit.xml \
        'src/**/*.test.ts'; echo $? > /reports/unit.status; \
    npm audit --omit=dev --audit-level=high > /reports/audit.log 2>&1; echo $? > /reports/audit.status; \
    true

FROM scratch AS check-report
COPY --from=check /reports /

# ── Smoke: the production build driven in Chromium against a mock gateway ────
# Built again here: the Playwright image is glibc, the runtime image musl.
FROM ${PLAYWRIGHT_IMAGE} AS smoke
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY . .
RUN mkdir -p /reports; \
    { npm run build > /reports/build.log 2>&1 \
      && SMOKE_JUNIT=/reports/smoke.xml node e2e/smoke.mjs > /reports/smoke.log 2>&1; \
      echo $? > /reports/smoke.status; }; \
    true

FROM scratch AS smoke-report
COPY --from=smoke /reports /

# ── Production build ──────────────────────────────────────────────────────────
FROM deps AS build
RUN npm run build

# ── Runtime (non-root; nothing is written to disk) ────────────────────────────
FROM ${NODE_IMAGE} AS runtime
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=8080 HOSTNAME=0.0.0.0
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/scripts/client-ip.mjs ./scripts/
USER node
EXPOSE 8080
CMD ["node", "--import", "./scripts/client-ip.mjs", "server.js"]

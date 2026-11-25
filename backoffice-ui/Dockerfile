# ── Build ───────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY package.json package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY . .
RUN npm run build

# ── Run (non-root; nothing is written to disk) ─────────────────────────────────
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=8080 HOSTNAME=0.0.0.0
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/scripts/client-ip.mjs ./scripts/
USER node
EXPOSE 8080
CMD ["node", "--import", "./scripts/client-ip.mjs", "server.js"]

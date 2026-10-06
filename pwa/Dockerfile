# ── Build ───────────────────────────────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY . .
ARG VITE_API_BASE_URL=/
ARG VITE_IDLE_TIMEOUT_MINUTES=15
RUN npm run build

# ── Serve (unprivileged nginx: runs as non-root, listens on 8080) ─────────────
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
ENV GATEWAY_URL=http://api-gateway:8080 \
    API_ORIGIN=""
EXPOSE 8080

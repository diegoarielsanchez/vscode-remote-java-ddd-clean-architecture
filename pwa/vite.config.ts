/// <reference types="vitest/config" />
import { defineConfig, loadEnv, type ProxyOptions } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const gateway = env.GATEWAY_URL || "http://localhost:8080";

  // Dev/preview: the PWA and the API share one origin through this proxy, so no CORS is needed.
  // The browser's Origin header is dropped because the gateway would treat the request as
  // cross-origin (it only allow-lists :5173/:3000). Production does the same in nginx.
  const toGateway: ProxyOptions = {
    target: gateway,
    changeOrigin: true,
    configure: (proxy) => proxy.on("proxyReq", (proxyReq) => proxyReq.removeHeader("origin")),
  };
  const proxy = { "/api": toGateway, "/auth": toGateway };

  return {
    plugins: [
      react(),
      VitePWA({
        registerType: "prompt", // ask the user before activating a new version
        injectRegister: false, // registered from src/app/UpdatePrompt.tsx (no inline script → strict CSP)
        includeAssets: ["icons/icon.svg"],
        manifest: {
          id: "/",
          name: "MedRep",
          short_name: "MedRep",
          description: "Visits, visit plans and settlements for medical sales reps",
          start_url: "/",
          scope: "/",
          display: "standalone",
          orientation: "portrait",
          theme_color: "#00658e",
          background_color: "#ffffff",
          icons: [
            { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
            { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
            { src: "/icons/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
          ],
        },
        workbox: {
          // Precache the app shell only.
          globPatterns: ["**/*.{js,css,html,svg,png,woff2}"],
          navigateFallback: "/index.html",
          navigateFallbackDenylist: [/^\/api\//, /^\/auth\//],
          cleanupOutdatedCaches: true,
          // Bundle the Workbox runtime into sw.js. Otherwise sw.js loads it with importScripts(),
          // a Trusted Types sink that the CSP (also applied to the worker) blocks, leaving an
          // empty service worker with no precache.
          inlineWorkboxRuntime: true,
          // OWASP: authenticated API traffic always goes to the network and is never written to
          // Cache Storage (it holds personal data and would outlive the session).
          runtimeCaching: [
            {
              urlPattern: ({ url }) => url.pathname.startsWith("/api/") || url.pathname.startsWith("/auth/"),
              handler: "NetworkOnly",
            },
          ],
        },
      }),
    ],
    server: { port: 5174, strictPort: true, proxy },
    preview: { port: 4174, strictPort: true, proxy },
    esbuild: { drop: mode === "production" ? ["console", "debugger"] : [] },
    build: { sourcemap: false },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      css: false,
      restoreMocks: true,
    },
  };
});

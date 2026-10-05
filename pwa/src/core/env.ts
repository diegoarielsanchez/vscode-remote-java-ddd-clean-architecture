const origin = typeof window !== "undefined" ? window.location.origin : "http://localhost";

function resolveBaseUrl(raw: string): URL {
  const url = new URL(raw, origin);
  if (!url.pathname.endsWith("/")) url.pathname += "/";
  return url;
}

/** Gateway base URL. "/" (default) = same origin, proxied to the gateway by Vite or nginx. */
export const API_BASE_URL = resolveBaseUrl(import.meta.env.VITE_API_BASE_URL || "/");

// OWASP A02: never send credentials over plain HTTP outside local development.
if (
  import.meta.env.PROD &&
  API_BASE_URL.protocol !== "https:" &&
  !["localhost", "127.0.0.1"].includes(API_BASE_URL.hostname)
) {
  throw new Error("VITE_API_BASE_URL must use https:// in production builds.");
}

export const IDLE_TIMEOUT_MS = (Number(import.meta.env.VITE_IDLE_TIMEOUT_MINUTES) || 15) * 60_000;

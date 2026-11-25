import "server-only";

function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing environment variable ${name}`);
  return value;
}

// Getters, not constants: `next build` must work without runtime secrets.
export const env = {
  get gatewayUrl() {
    return required("GATEWAY_URL").replace(/\/+$/, "");
  },
  get sessionSecret() {
    const secret = required("SESSION_SECRET");
    if (secret.length < 32) throw new Error("SESSION_SECRET must have at least 32 characters");
    return secret;
  },
  get catalogAccount() {
    return { username: required("CATALOG_SERVICE_USERNAME"), password: required("CATALOG_SERVICE_PASSWORD") };
  },
  get revalidateSecret() {
    return process.env.REVALIDATE_SECRET || null;
  },
  get idleSeconds() {
    return Math.max(1, Number(process.env.IDLE_TIMEOUT_MINUTES) || 15) * 60;
  },
  get secure() {
    return process.env.COOKIE_SECURE ? process.env.COOKIE_SECURE !== "false" : process.env.NODE_ENV === "production";
  },
  get trustedProxyHops() {
    return Math.max(0, Number(process.env.TRUSTED_PROXY_HOPS) || 0);
  },
};

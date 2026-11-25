/** Stops at startup, not at the first sign-in, when configuration is missing. Not run by `next build`. */
export async function register() {
  if (process.env.NEXT_RUNTIME !== "nodejs") return;
  const { env } = await import("@/server/env");
  try {
    void [env.gatewayUrl, env.sessionSecret, env.catalogAccount];
  } catch (e) {
    console.error(`Configuration error: ${(e as Error).message}`);
    process.exit(1);
  }
}

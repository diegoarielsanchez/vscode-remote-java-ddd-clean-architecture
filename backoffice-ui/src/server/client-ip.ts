/**
 * The browser's address: each proxy appends the peer it saw, so only the right-hand end of
 * X-Forwarded-For can be trusted. `hops` is the number of trusted proxies in front of this app.
 */
export function clientIp(forwardedFor: string | null, hops: number, peerAppended: boolean): string | null {
  if (!peerAppended) return null; // without scripts/client-ip.mjs (next dev) the header can be forged
  const chain = (forwardedFor ?? "").split(",").map((s) => s.trim()).filter(Boolean);
  return chain.at(-1 - hops) ?? null;
}

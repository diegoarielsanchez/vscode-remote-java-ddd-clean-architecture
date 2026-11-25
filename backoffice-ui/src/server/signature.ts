import { createHmac, timingSafeEqual } from "node:crypto";

const MAX_AGE_SECONDS = 300;

export const sign = (secret: string, timestamp: string, body: string) =>
  createHmac("sha256", secret).update(`${timestamp}.${body}`).digest("hex");

/** HMAC over timestamp + body, so a captured call can be neither altered nor replayed later. */
export function verify(secret: string, timestamp: string | null, signature: string | null, body: string, now: number): boolean {
  if (!timestamp || !signature || !/^\d{1,12}$/.test(timestamp)) return false;
  if (Math.abs(now - Number(timestamp)) > MAX_AGE_SECONDS) return false;
  const expected = Buffer.from(sign(secret, timestamp, body));
  const actual = Buffer.from(signature);
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

import "server-only";
import { createHash } from "node:crypto";
import { EncryptJWT, decodeJwt, jwtDecrypt } from "jose";
import { env } from "./env.ts";

export type Session = {
  /** The user's own backend JWT. Never leaves the server unencrypted. */
  token: string;
  username: string;
  roles: string[];
  /** Absolute end (seconds): the backend JWT's own expiry. */
  expiresAt: number;
  /** Last page load (seconds): drives the idle timeout. */
  seenAt: number;
};

export const nowSeconds = () => Math.floor(Date.now() / 1000);

export const cookieName = () => (env.secure ? "__Host-bo_session" : "bo_session");

export const cookieOptions = (session: Session) => ({
  httpOnly: true,
  secure: env.secure,
  sameSite: "strict" as const,
  path: "/",
  maxAge: Math.max(0, Math.min(session.expiresAt - nowSeconds(), env.idleSeconds)),
});

const key = () => createHash("sha256").update(`backoffice-session:${env.sessionSecret}`).digest();

export const sealSession = (session: Session) =>
  new EncryptJWT({ ...session })
    .setProtectedHeader({ alg: "dir", enc: "A256GCM" })
    .setExpirationTime(session.expiresAt)
    .encrypt(key());

/** Null when the cookie is missing, forged, expired or idle for too long. */
export async function openSession(cookie: string | undefined): Promise<Session | null> {
  if (!cookie) return null;
  const secret = key(); // a missing SESSION_SECRET must fail loudly, not look like "signed out"
  try {
    const { payload } = await jwtDecrypt(cookie, secret, {
      keyManagementAlgorithms: ["dir"],
      contentEncryptionAlgorithms: ["A256GCM"],
    });
    const { token, username, roles, expiresAt, seenAt } = payload as unknown as Session;
    if (seenAt + env.idleSeconds <= nowSeconds()) return null;
    return { token, username, roles, expiresAt, seenAt };
  } catch {
    return null;
  }
}

/** The backend JWT's `exp`. Its signature is the gateway's to check, on every call. */
export function expiryOf(token: string): number {
  const { exp } = decodeJwt(token);
  if (!exp) throw new Error("The identity service returned a token without expiry");
  return exp;
}

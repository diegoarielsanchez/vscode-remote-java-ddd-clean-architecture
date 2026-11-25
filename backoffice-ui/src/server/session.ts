import "server-only";
import { cookies, headers } from "next/headers";
import { forbidden, notFound, redirect } from "next/navigation";
import { cache } from "react";
import { clientIp } from "./client-ip.ts";
import { env } from "./env.ts";
import { callGateway, type GatewayRequest } from "./gateway.ts";
import { GatewayError, isNotFound } from "./gateway-error.ts";
import { cookieName, cookieOptions, openSession, sealSession, type Session } from "./token.ts";

export const isAdmin = (session: Session) => session.roles.includes("ROLE_ADMIN");

/** One decryption per request, however many components ask. */
export const getSession = cache(async () => openSession((await cookies()).get(cookieName())?.value));

/**
 * Every protected page, action and route handler calls this (directly or through `asUser`):
 * the proxy's redirect is a convenience, not the access check.
 */
export async function requireSession(): Promise<Session> {
  return (await getSession()) ?? redirect("/login");
}

/** UI-side check; the backend authorizes again. */
export async function requireAdmin(): Promise<Session> {
  const session = await requireSession();
  if (!isAdmin(session)) throw new GatewayError(403, "You don't have permission to do that.");
  return session;
}

export async function startSession(session: Session) {
  (await cookies()).set(cookieName(), await sealSession(session), cookieOptions(session));
}

export async function endSession() {
  (await cookies()).set(cookieName(), "", { httpOnly: true, secure: env.secure, sameSite: "strict", path: "/", maxAge: 0 });
}

export async function requestIp(): Promise<string | null> {
  return clientIp((await headers()).get("x-forwarded-for"), env.trustedProxyHops, process.env.PEER_IP_APPENDED === "1");
}

/** Calls the gateway with the signed-in user's own JWT, so the backend authorizes that user. */
export async function asUser<T = void>(path: string, options: Omit<GatewayRequest<T>, "token" | "clientIp"> = {}): Promise<T> {
  const session = await requireSession();
  return callGateway(path, { ...options, token: session.token, clientIp: await requestIp() });
}

/** For page data: turns gateway errors into navigation (sign-in, 403, 404) and lets the rest reach error.tsx. */
export async function load<T>(promise: Promise<T>): Promise<T> {
  try {
    return await promise;
  } catch (e) {
    if (e instanceof GatewayError && e.status === 401) redirect("/bff/expired");
    if (e instanceof GatewayError && e.status === 403) forbidden();
    if (isNotFound(e)) notFound();
    throw e;
  }
}

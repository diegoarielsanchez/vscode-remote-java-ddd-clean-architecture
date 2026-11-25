import { randomBytes } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { contentSecurityPolicy } from "@/server/csp";
import { env } from "@/server/env";
import { isPublic, routeKind } from "@/server/routes";
import { cookieName, cookieOptions, nowSeconds, openSession, sealSession } from "@/server/token";

/**
 * Runs before every page, including prerendered ones: sign-in redirect, idle-timeout refresh and
 * the security headers of each rendering mode. Pages with personal data check the session again
 * themselves (server/session.ts).
 */
export async function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  const kind = routeKind(pathname);
  const session = await openSession(request.cookies.get(cookieName())?.value);

  if (!session && !isPublic(pathname)) {
    if (pathname.startsWith("/bff/")) {
      return NextResponse.json({ error: "Not signed in" }, { status: 401, headers: { "Cache-Control": "no-store" } });
    }
    const login = new URL("/login", request.url);
    if (request.method === "GET" && pathname !== "/") login.searchParams.set("next", pathname + search);
    return NextResponse.redirect(login, 303);
  }

  const nonce = kind === "dynamic" ? randomBytes(16).toString("base64") : null;
  const csp = contentSecurityPolicy({ nonce, dev: process.env.NODE_ENV === "development", secure: env.secure });
  const requestHeaders = new Headers(request.headers);
  requestHeaders.delete("x-nonce");
  requestHeaders.delete("content-security-policy");
  if (nonce) requestHeaders.set("content-security-policy", csp); // Next stamps the nonce on its scripts

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("X-Frame-Options", "DENY");
  // Not no-referrer: with it browsers post forms with "Origin: null", which the CSRF check refuses.
  response.headers.set("Referrer-Policy", "same-origin");
  response.headers.set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=()");
  response.headers.set("Cross-Origin-Opener-Policy", "same-origin");
  response.headers.set("Cross-Origin-Resource-Policy", "same-origin");
  if (env.secure) response.headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  // ISR is a *server* cache here. Next would announce s-maxage, inviting a CDN or the browser to
  // serve a signed-in page without asking this proxy.
  if (kind !== "static") response.headers.set("Cache-Control", "private, no-store");

  // Page loads keep the session alive; background calls, prefetches and posts (sign-out) don't.
  const isPageLoad =
    request.method === "GET" && !pathname.startsWith("/bff/") && !isPublic(pathname) &&
    !request.headers.has("next-router-prefetch") && request.headers.get("sec-purpose") !== "prefetch";
  if (session && isPageLoad && nowSeconds() - session.seenAt >= 60) {
    const renewed = { ...session, seenAt: nowSeconds() };
    response.cookies.set(cookieName(), await sealSession(renewed), cookieOptions(renewed));
  }
  return response;
}

export const config = { matcher: ["/((?!_next/static|favicon.ico|icon.svg).*)"] };

/** Which rendering group a path belongs to. The proxy derives CSP, caching and access from it. */
export type RouteKind = "static" | "isr" | "dynamic";

export function routeKind(pathname: string): RouteKind {
  if (pathname === "/help") return "static";
  if (/^\/catalog(\/|$)/.test(pathname)) return "isr";
  return "dynamic";
}

const PUBLIC = new Set(["/login", "/help", "/healthz", "/bff/revalidate", "/bff/expired"]);
export const isPublic = (pathname: string) => PUBLIC.has(pathname);

/** Only a path on this site, so ?next= can't send someone elsewhere after signing in. */
export function safeNext(next: string | null | undefined): string {
  if (!next || !/^\/(?![/\\])[^\\\u0000-\u001f]*$/.test(next)) return "/";
  return /^\/(login|bff)(\/|\?|$)/.test(next) ? "/" : next;
}

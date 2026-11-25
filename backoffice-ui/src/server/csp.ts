/**
 * Two policies, because a nonce must change on every response and a prerendered page is the
 * same bytes for everyone:
 *  - dynamic pages (everything with personal data, and sign-in): nonce + strict-dynamic;
 *  - prerendered pages (help, catalog master data): inline scripts allowed, as Next's own
 *    bootstrap is inline.
 * The two kinds live under different root layouts, so moving between them is a full page load
 * and the weaker policy never governs a page with personal data.
 */
export function contentSecurityPolicy(options: { nonce: string | null; dev: boolean; secure: boolean }): string {
  const { nonce, dev, secure } = options;
  const script = nonce ? `'self' 'nonce-${nonce}' 'strict-dynamic'` : "'self' 'unsafe-inline'";
  return [
    "default-src 'self'",
    `script-src ${script}${dev ? " 'unsafe-eval'" : ""}`,
    `style-src 'self'${dev ? " 'unsafe-inline'" : nonce ? ` 'nonce-${nonce}'` : ""}`,
    "img-src 'self' data:",
    "font-src 'self'",
    `connect-src 'self'${dev ? " ws:" : ""}`,
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    ...(secure ? ["upgrade-insecure-requests"] : []),
  ].join("; ");
}

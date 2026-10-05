// OWASP A03: with the CSP directive `require-trusted-types-for 'script'`, DOM XSS sinks
// (innerHTML, eval, script URLs, ...) only accept values produced by a Trusted Types policy.
// This default policy allows same-origin script URLs (the service worker registration) and
// nothing else; HTML sinks stay blocked because no createHTML rule is defined.
type TrustedTypesFactory = {
  createPolicy(name: string, rules: { createScriptURL?: (input: string) => string }): unknown;
};

const factory = (window as unknown as { trustedTypes?: TrustedTypesFactory }).trustedTypes;

if (factory) {
  try {
    factory.createPolicy("default", {
      createScriptURL(input) {
        const url = new URL(input, window.location.origin);
        if (url.origin !== window.location.origin) throw new TypeError(`Blocked script URL: ${url.origin}`);
        return url.href;
      },
    });
  } catch {
    // Policy already exists (e.g. hot reload).
  }
}

export {};

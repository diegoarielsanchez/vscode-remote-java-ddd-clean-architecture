export type FieldErrors = Record<string, string>;

export class GatewayError extends Error {
  readonly status: number;
  readonly fields: FieldErrors | undefined;
  /** Next hides messages of server errors from the browser but forwards `digest` (see error.tsx). */
  readonly digest: string;

  constructor(status: number, message: string, fields?: FieldErrors) {
    super(message);
    this.name = "GatewayError";
    this.status = status;
    this.fields = fields;
    this.digest = `GATEWAY_${status}`;
  }
}

/**
 * Some use cases answer 400 "... not found" / "No orders found." instead of 404 or an empty list.
 */
export const isNotFound = (e: unknown): boolean =>
  e instanceof GatewayError && (e.status === 404 || (e.status === 400 && /\bfound\b/i.test(e.message)));

export function emptyOnNotFound(e: unknown): [] {
  if (isNotFound(e)) return [];
  throw e;
}

/**
 * The backend's error shapes: {statusCode, message}, {"field": "message"} (bean validation)
 * and {error} (failed login). Server errors are never passed on as text.
 */
export function toGatewayError(status: number, body: unknown): GatewayError {
  const o = (body && typeof body === "object" ? body : {}) as Record<string, unknown>;
  if (status === 401) return new GatewayError(401, "Your session has expired. Please sign in again.");
  if (status === 403) return new GatewayError(403, "You don't have permission to do that.");
  if (status === 429) return new GatewayError(429, "Too many requests. Please wait a minute and try again.");
  if (status >= 500) return new GatewayError(status, "The service is temporarily unavailable. Please try again later.");
  if (typeof o.message === "string") return new GatewayError(status, o.message);
  if (typeof o.error === "string") return new GatewayError(status, o.error);
  const entries = Object.entries(o);
  if (entries.length > 0 && entries.every(([, v]) => typeof v === "string")) {
    return new GatewayError(status, "Please correct the highlighted fields.", o as FieldErrors);
  }
  return new GatewayError(status, `Request failed (HTTP ${status}).`);
}

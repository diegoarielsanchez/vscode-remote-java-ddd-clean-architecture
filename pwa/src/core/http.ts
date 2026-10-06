import { z } from "zod";
import { API_BASE_URL } from "./env";

export type FieldErrors = Record<string, string>;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly fields?: FieldErrors,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

// ── Token: memory only ───────────────────────────────────────────────────────────
// OWASP A07: never localStorage/sessionStorage (readable by any injected script, survives the
// tab). The trade-off is signing in again after a reload; a BFF with an HttpOnly cookie removes it.
let accessToken: string | null = null;
let unauthorizedHandler: () => void = () => {};

export const tokenStore = {
  set(token: string) {
    accessToken = token;
  },
  clear() {
    accessToken = null;
  },
  has: () => accessToken !== null,
  onUnauthorized(handler: () => void) {
    unauthorizedHandler = handler;
  },
};

// ── Request ──────────────────────────────────────────────────────────────────────
export type RequestOptions<T> = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  query?: Record<string, string | number | undefined>;
  json?: unknown;
  form?: FormData;
  /** Validates (and types) the response body. OWASP A08: don't trust what you receive. */
  schema?: z.ZodType<T, z.ZodTypeDef, unknown>;
  signal?: AbortSignal;
};

/** Every call to the gateway goes through here. `path` is relative, e.g. "api/v1/visit/list". */
export async function http<T = void>(path: string, options: RequestOptions<T> = {}): Promise<T> {
  const url = new URL(path, API_BASE_URL);
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined) url.searchParams.set(key, String(value));
  }

  const isAuthCall = path.startsWith("auth/");
  const headers: Record<string, string> = { Accept: "application/json" };
  if (accessToken && !isAuthCall) headers.Authorization = `Bearer ${accessToken}`;

  let body: BodyInit | undefined;
  if (options.json !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.json);
  } else if (options.form) {
    body = options.form; // the browser sets the multipart boundary
  }

  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? "GET",
      headers,
      body,
      signal: options.signal,
      credentials: "omit", // bearer token, no cookies: nothing for CSRF to ride on
      cache: "no-store", // keep personal data out of the HTTP cache
      referrerPolicy: "no-referrer",
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, navigator.onLine === false ? "You're offline." : "Cannot reach the server.");
  }

  if (!response.ok) throw await toApiError(response, isAuthCall);
  if (response.status === 204 || options.schema === undefined) return undefined as T;

  const data: unknown = await response.json().catch(() => {
    throw new ApiError(response.status, "Unexpected response from the server.");
  });
  const parsed = options.schema.safeParse(data);
  if (!parsed.success) throw new ApiError(response.status, "Unexpected response from the server.");
  return parsed.data;
}

/**
 * The backend answers with three error shapes:
 *   {statusCode, message}   domain errors, 404, 429
 *   {"field": "message"}    bean-validation errors
 *   {error}                 failed login
 */
async function toApiError(response: Response, isAuthCall: boolean): Promise<ApiError> {
  const body = (await response.json().catch(() => null)) as Record<string, unknown> | null;
  const status = response.status;

  if (status === 401) {
    if (isAuthCall) return new ApiError(401, typeof body?.error === "string" ? body.error : "Invalid credentials");
    unauthorizedHandler();
    return new ApiError(401, "Your session has expired. Please sign in again.");
  }
  if (status === 403) return new ApiError(403, "You don't have permission to do that.");
  if (status === 429) return new ApiError(429, "Too many requests. Please wait a minute and try again.");
  if (status === 502 || status === 503 || status === 504) {
    return new ApiError(status, "The service is temporarily unavailable. Please try again later.");
  }
  if (typeof body?.message === "string") return new ApiError(status, body.message);
  if (typeof body?.error === "string") return new ApiError(status, body.error);
  if (body && Object.keys(body).length > 0 && Object.values(body).every((v) => typeof v === "string")) {
    return new ApiError(status, "Please correct the highlighted fields.", body as FieldErrors);
  }
  return new ApiError(status, `Request failed (HTTP ${status}).`);
}

/**
 * The visit, visit-plan, HCP and MSR list use cases throw a 400 "... not found" instead of
 * returning [] (also when paging past the end). Treat that as an empty result.
 */
export function emptyOnNotFound<T>(e: unknown): T[] {
  if (e instanceof ApiError && e.status === 400 && /found/i.test(e.message)) return [];
  throw e;
}

/** Path segment encoding for ids coming from the server or the URL. */
export const seg = (value: string) => encodeURIComponent(value);

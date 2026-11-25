import "server-only";
import type { z } from "zod";
import { env } from "./env.ts";
import { GatewayError, toGatewayError } from "./gateway-error.ts";

export type GatewayRequest<T> = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  token?: string | null;
  query?: Record<string, string | number | undefined>;
  json?: unknown;
  form?: FormData;
  /** Validates and types the response body. */
  schema?: z.ZodType<T>;
  /** Forwarded as X-Forwarded-For: the gateway rate-limits per client address. */
  clientIp?: string | null;
  signal?: AbortSignal;
};

const TIMEOUT_MS = 10_000;
const UPLOAD_TIMEOUT_MS = 60_000;

/** Every call to the API gateway goes through here; nothing else in this app knows its URL. */
export async function callGateway<T = void>(path: string, options: GatewayRequest<T> = {}): Promise<T> {
  const url = new URL(`${env.gatewayUrl}/${path}`);
  for (const [k, v] of Object.entries(options.query ?? {})) if (v !== undefined) url.searchParams.set(k, String(v));

  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.token) headers.Authorization = `Bearer ${options.token}`;
  if (options.clientIp) headers["X-Forwarded-For"] = options.clientIp;
  if (options.json !== undefined) headers["Content-Type"] = "application/json";

  const timeout = AbortSignal.timeout(options.form ? UPLOAD_TIMEOUT_MS : TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? "GET",
      headers,
      body: options.form ?? (options.json === undefined ? undefined : JSON.stringify(options.json)),
      cache: "no-store", // responses hold personal data; only server/catalog.ts caches, after filtering
      redirect: "error",
      signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout,
    });
  } catch (e) {
    if (options.signal?.aborted) throw e;
    if (e instanceof Error && "digest" in e) throw e; // Next's own control-flow errors
    throw new GatewayError(503, "The service is temporarily unavailable. Please try again later.");
  }

  if (!response.ok) throw toGatewayError(response.status, await response.json().catch(() => null));
  if (!options.schema || response.status === 204) return undefined as T;

  const parsed = options.schema.safeParse(await response.json().catch(() => undefined));
  if (!parsed.success) {
    console.error(`Unexpected response shape from ${path.split("/").slice(0, 3).join("/")}`,
      parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.code}`).join(", "));
    throw new GatewayError(502, "Unexpected response from the service.");
  }
  return parsed.data;
}

/** Encodes one path segment. */
export const seg = encodeURIComponent;

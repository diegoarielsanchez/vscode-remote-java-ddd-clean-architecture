// @vitest-environment node
import { http as mock, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { server } from "../test/server";
import { ApiError, emptyOnNotFound, http, tokenStore } from "./http";

const Item = z.object({ id: z.string() });

describe("http", () => {
  it("attaches the bearer token to API calls but not to /auth", async () => {
    const seen: Record<string, string | null> = {};
    server.use(
      mock.get("*/api/v1/visit/1", ({ request }) => {
        seen.api = request.headers.get("authorization");
        return HttpResponse.json({ id: "1" });
      }),
      mock.post("*/auth/login", ({ request }) => {
        seen.auth = request.headers.get("authorization");
        return HttpResponse.json({ id: "x" });
      }),
    );
    tokenStore.set("jwt-123");
    await http("api/v1/visit/1", { schema: Item });
    await http("auth/login", { method: "POST", json: {}, schema: Item });
    expect(seen.api).toBe("Bearer jwt-123");
    expect(seen.auth).toBeNull();
  });

  it("maps a domain error body to its message", async () => {
    server.use(mock.post("*/api/v1/visit/create", () => HttpResponse.json({ statusCode: 400, message: "HCP is not active." }, { status: 400 })));
    await expect(http("api/v1/visit/create", { method: "POST", json: {} })).rejects.toMatchObject({ status: 400, message: "HCP is not active." });
  });

  it("maps a validation body to field errors", async () => {
    server.use(mock.post("*/api/v1/visitplan/create", () => HttpResponse.json({ visitDateTime: "must be a future date" }, { status: 400 })));
    const err = (await http("api/v1/visitplan/create", { method: "POST", json: {} }).catch((e) => e)) as ApiError;
    expect(err.fields).toEqual({ visitDateTime: "must be a future date" });
  });

  it("reports a failed login with the backend's generic message, without ending a session", async () => {
    const onUnauthorized = vi.fn();
    tokenStore.onUnauthorized(onUnauthorized);
    server.use(mock.post("*/auth/login", () => HttpResponse.json({ error: "Invalid credentials" }, { status: 401 })));
    await expect(http("auth/login", { method: "POST", json: {} })).rejects.toMatchObject({ message: "Invalid credentials" });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it("a 401 on an API call triggers the unauthorized handler", async () => {
    const onUnauthorized = vi.fn();
    tokenStore.onUnauthorized(onUnauthorized);
    server.use(mock.get("*/api/v1/visit/1", () => new HttpResponse(null, { status: 401 })));
    await expect(http("api/v1/visit/1")).rejects.toMatchObject({ status: 401 });
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("rejects responses that don't match the schema", async () => {
    server.use(mock.get("*/api/v1/visit/1", () => HttpResponse.json({ unexpected: true })));
    await expect(http("api/v1/visit/1", { schema: Item })).rejects.toMatchObject({ message: "Unexpected response from the server." });
  });

  it("maps rate limiting and the circuit-breaker fallback", async () => {
    server.use(
      mock.get("*/api/v1/a", () => new HttpResponse(null, { status: 429 })),
      mock.get("*/api/v1/b", () => new HttpResponse(null, { status: 503 })),
    );
    await expect(http("api/v1/a")).rejects.toMatchObject({ status: 429 });
    await expect(http("api/v1/b")).rejects.toMatchObject({ status: 503 });
  });

  it("treats the backend's 'not found' list error as an empty list", () => {
    expect(emptyOnNotFound(new ApiError(400, "Visit not found."))).toEqual([]);
    expect(() => emptyOnNotFound(new ApiError(400, "Invalid page"))).toThrow("Invalid page");
  });
});

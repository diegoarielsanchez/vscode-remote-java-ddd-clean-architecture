"use server";
import { redirect } from "next/navigation";
import { z } from "zod";
import { attempt, invalid, type ActionState } from "@/server/action-state";
import { callGateway } from "@/server/gateway";
import { GatewayError } from "@/server/gateway-error";
import { safeNext } from "@/server/routes";
import { endSession, requestIp, startSession } from "@/server/session";
import { expiryOf, nowSeconds } from "@/server/token";

const Credentials = z.object({
  username: z.string({ error: "Enter your username" }).trim().min(1, "Enter your username").max(64),
  password: z.string({ error: "Enter your password" }).min(1, "Enter your password").max(128),
});
const LoginResponse = z.object({ token: z.string().min(1), username: z.string(), roles: z.array(z.string()).default([]) });

/** The JWT stays on the server, encrypted in an HttpOnly cookie the browser's scripts can't read. */
export async function signIn(_: ActionState, form: FormData): Promise<ActionState> {
  const input = Credentials.safeParse({ username: form.get("username"), password: form.get("password") });
  if (!input.success) return invalid(input.error);

  try {
    const { token, username, roles } = await callGateway("auth/login", {
      method: "POST", json: input.data, schema: LoginResponse, clientIp: await requestIp(),
    });
    await startSession({ token, username, roles, expiresAt: expiryOf(token), seenAt: nowSeconds() });
  } catch (e) {
    if (e instanceof GatewayError && e.status === 401) return { error: "Invalid credentials" };
    const failure = await attempt(() => Promise.reject(e));
    return failure ?? {};
  }
  redirect(safeNext(form.get("next") as string | null));
}

export async function signOut() {
  await endSession();
  redirect("/login");
}

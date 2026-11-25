import "server-only";
import type { z } from "zod";
import { redirect } from "next/navigation";
import { GatewayError, type FieldErrors } from "./gateway-error.ts";

export type ActionState = { error?: string; fields?: FieldErrors; ok?: string };

export const invalid = (error: z.ZodError): ActionState => ({
  error: "Please correct the highlighted fields.",
  fields: Object.fromEntries(error.issues.map((i) => [i.path.join(".") || "form", i.message])),
});

/** Runs a Server Action's gateway call and turns expected failures into form state. */
export async function attempt(run: () => Promise<void>): Promise<ActionState | null> {
  try {
    await run();
    return null;
  } catch (e) {
    if (!(e instanceof GatewayError)) throw e; // includes Next's redirects
    if (e.status === 401) redirect("/bff/expired");
    return { error: e.message, fields: e.fields };
  }
}

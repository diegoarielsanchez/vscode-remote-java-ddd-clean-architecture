import { z } from "zod";

// Amounts are Java BigDecimal: kept as strings so JavaScript never rounds them further.
export const Money = z.union([z.number(), z.string()]).transform(String);
export const Uuid = z.string().regex(/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i, "Invalid id");
export const IsoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Enter a date");
export const Reason = z.string().trim().min(1, "Give a reason").max(500, "At most 500 characters");
export const isUuid = (v: string) => Uuid.safeParse(v).success;

/** Form fields as a plain object: blank → undefined. */
export function formObject(form: FormData): Record<string, string | undefined> {
  const out: Record<string, string | undefined> = {};
  for (const [k, v] of form) if (typeof v === "string" && !k.startsWith("$")) out[k] = v.trim() === "" ? undefined : v;
  return out;
}

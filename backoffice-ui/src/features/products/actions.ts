"use server";
import { revalidatePath, updateTag } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { IsoDate, Reason, Uuid, formObject } from "@/features/schemas";
import { attempt, invalid, type ActionState } from "@/server/action-state";
import { CATALOG_TAG } from "@/server/cacheable";
import { asUser, requireAdmin } from "@/server/session";

const Temperature = z.string().regex(/^-?\d{1,3}(\.\d)?$/, "A number with at most one decimal").optional();
const Details = z.object({
  name: z.string({ error: "Required" }).trim().min(1, "Required"),
  description: z.string().optional(),
  price: z.string({ error: "Required" }).regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter a price, e.g. 12.50"),
  unit: z.string({ error: "Required" }).trim().min(1, "Required"),
  gtin: z.string().regex(/^\d{8}(\d{4,6})?$/, "8, 12, 13 or 14 digits").optional(),
  minTemperatureC: Temperature,
  maxTemperatureC: Temperature,
  maxHumidityPercent: z.string().regex(/^\d{1,3}$/, "A whole number").optional(),
  protectFromLight: z.literal("on").optional(),
});
const FirstLot = z.object({
  lotTracked: z.literal("on").optional(),
  initialStock: z.string().regex(/^\d{1,9}$/, "A whole number").optional(),
  batchNumber: z.string().optional(),
  expirationDate: IsoDate.optional(),
});

function toJson({ minTemperatureC, maxTemperatureC, maxHumidityPercent, protectFromLight, ...rest }: z.infer<typeof Details>) {
  const hasStorage = minTemperatureC ?? maxTemperatureC ?? maxHumidityPercent ?? protectFromLight;
  return {
    ...rest,
    storageCondition: hasStorage === undefined ? undefined : {
      minTemperatureC, maxTemperatureC, protectFromLight: protectFromLight === "on",
      maxHumidityPercent: maxHumidityPercent === undefined ? undefined : Number(maxHumidityPercent),
    },
  };
}

export async function createProduct(_: ActionState, form: FormData): Promise<ActionState> {
  const input = Details.extend(FirstLot.shape).safeParse(formObject(form));
  if (!input.success) return invalid(input.error);
  const { lotTracked, initialStock, batchNumber, expirationDate, ...details } = input.data;
  let id = "";
  const failure = await attempt(async () => {
    await requireAdmin();
    ({ id } = await asUser("api/v1/products/create", {
      method: "POST",
      json: { ...toJson(details), lotTracked: lotTracked === "on", initialStock: Number(initialStock ?? 0), batchNumber, expirationDate },
      schema: z.object({ id: z.string() }),
    }));
  });
  if (failure) return failure;
  updateTag(CATALOG_TAG);
  redirect(`/products/${id}`);
}

export async function updateProduct(productId: string, _: ActionState, form: FormData): Promise<ActionState> {
  const id = Uuid.parse(productId);
  const input = Details.extend({ reason: Reason }).safeParse(formObject(form));
  if (!input.success) return invalid(input.error);
  const { reason, ...details } = input.data;
  const failure = await attempt(async () => {
    await requireAdmin();
    await asUser("api/v1/products/update", { method: "PUT", json: { id, ...toJson(details), reason } });
  });
  if (failure) return failure;
  updateTag(CATALOG_TAG); // the ISR pages are rebuilt on their next visit
  revalidatePath(`/products/${id}`);
  return { ok: "Saved. The catalog pages are refreshed." };
}

export async function setProductActive(productId: string, active: boolean, _: ActionState): Promise<ActionState> {
  const id = Uuid.parse(productId);
  const failure = await attempt(async () => {
    await requireAdmin();
    await asUser(`api/v1/products/${id}/${active === true ? "activate" : "deactivate"}`, { method: "POST" });
  });
  if (failure) return failure;
  revalidatePath(`/products/${id}`); // `active` isn't in the ISR cache: nothing to refresh there
  return { ok: active ? "Activated." : "Deactivated." };
}

const LotDecision = z.enum(["quarantine", "lift-quarantine", "reject"]);

/** Quality's lot decisions (ADMIN in the backend). */
export async function decideLot(productId: string, batchNumber: string | null, _: ActionState, form: FormData): Promise<ActionState> {
  const id = Uuid.parse(productId);
  const decision = LotDecision.safeParse(form.get("decision"));
  const reason = Reason.safeParse(form.get("reason"));
  if (!decision.success || !reason.success) return { error: "Choose a decision and give a reason (at most 500 characters)." };
  const failure = await attempt(async () => {
    await requireAdmin();
    await asUser(`api/v1/products/${id}/lots/${decision.data}`, { method: "POST", json: { batchNumber, reason: reason.data } });
  });
  if (failure) return failure;
  revalidatePath(`/products/${id}/lots`);
  return { ok: "Recorded." };
}

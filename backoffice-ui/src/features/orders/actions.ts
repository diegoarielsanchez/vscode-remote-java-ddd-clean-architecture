"use server";
import { revalidatePath } from "next/cache";
import { Reason, Uuid } from "@/features/schemas";
import { attempt, type ActionState } from "@/server/action-state";
import { asUser, requireAdmin } from "@/server/session";

/** Quality's decision on a delivery quarantined for a transport break (ADMIN in the backend). */
export async function decideDelivery(orderId: string, decision: "release" | "reject", _: ActionState, form: FormData): Promise<ActionState> {
  const id = Uuid.parse(orderId);
  const verb = decision === "release" ? "release" : "reject";
  const reason = Reason.safeParse(form.get("reason"));
  if (!reason.success) return { error: "Give a reason (at most 500 characters)." };
  const failure = await attempt(async () => {
    await requireAdmin();
    await asUser(`api/v1/orders/${id}/delivery/${verb}`, { method: "POST", json: { reason: reason.data } });
  });
  if (failure) return failure;
  revalidatePath(`/orders/${id}`);
  return { ok: verb === "release" ? "Delivery released." : "Delivery rejected." };
}

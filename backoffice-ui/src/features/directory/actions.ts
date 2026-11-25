"use server";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { Reason, Uuid, formObject } from "@/features/schemas";
import { attempt, invalid, type ActionState } from "@/server/action-state";
import { asUser, requireAdmin } from "@/server/session";

const Kind = z.enum(["hcp", "msr"]);
const resourceOf = { hcp: "healthcareprof", msr: "medicalsalesrep" } as const;
const Details = z.object({
  name: z.string({ error: "Required" }).trim().min(1, "Required").max(100),
  surname: z.string({ error: "Required" }).trim().min(1, "Required").max(100),
  email: z.email({ error: "Enter an e-mail address" }),
});
const Specialties = z.array(z.string().max(10)).min(1, "Choose at least one specialty");

function details(kind: "hcp" | "msr", form: FormData) {
  const schema = kind === "hcp" ? Details.extend({ specialties: Specialties }) : Details;
  return schema.safeParse({ ...formObject(form), specialties: form.getAll("specialties") });
}

export async function createPerson(kindInput: "hcp" | "msr", _: ActionState, form: FormData): Promise<ActionState> {
  const kind = Kind.parse(kindInput);
  const input = details(kind, form);
  if (!input.success) return invalid(input.error);
  let id = "";
  const failure = await attempt(async () => {
    ({ id } = await asUser(`api/v1/${resourceOf[kind]}/create`, { method: "POST", json: input.data, schema: z.object({ id: z.string() }) }));
  });
  if (failure) return failure;
  redirect(`/${kind}s/${id}`);
}

export async function updatePerson(kindInput: "hcp" | "msr", idInput: string, _: ActionState, form: FormData): Promise<ActionState> {
  const kind = Kind.parse(kindInput);
  const id = Uuid.parse(idInput);
  const input = details(kind, form);
  if (!input.success) return invalid(input.error);
  const reason = Reason.safeParse(form.get("reason"));
  if (!reason.success) return { error: "Please correct the highlighted fields.", fields: { reason: "Give a reason (at most 500 characters)" } };
  const failure = await attempt(() =>
    asUser(`api/v1/${resourceOf[kind]}/update`, { method: "PUT", json: { id, ...input.data, reason: reason.data } }));
  if (failure) return failure;
  revalidatePath(`/${kind}s/${id}`);
  return { ok: "Saved." };
}

export async function setPersonActive(kindInput: "hcp" | "msr", idInput: string, active: boolean, _: ActionState): Promise<ActionState> {
  const kind = Kind.parse(kindInput);
  const id = Uuid.parse(idInput);
  const failure = await attempt(async () => {
    await requireAdmin();
    await asUser(`api/v1/${resourceOf[kind]}/${id}/${active === true ? "activate" : "deactivate"}`, { method: "POST" });
  });
  if (failure) return failure;
  revalidatePath(`/${kind}s/${id}`);
  return { ok: active ? "Activated." : "Deactivated." };
}

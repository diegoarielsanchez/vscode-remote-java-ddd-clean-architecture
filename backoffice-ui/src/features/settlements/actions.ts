"use server";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { z } from "zod";
import { IsoDate, Uuid, formObject } from "@/features/schemas";
import { attempt, invalid, type ActionState } from "@/server/action-state";
import { GatewayError } from "@/server/gateway-error";
import { asUser } from "@/server/session";
import { uploadProblem } from "./upload";

const Header = z.object({
  description: z.string({ error: "Required" }).trim().min(1, "Required").max(255),
  settlementDate: IsoDate,
  medicalSalesRepId: Uuid,
});
const Invoice = z.object({
  invoiceNumber: z.string().regex(/^[A-Za-z]\d{12}$/, "One letter and 12 digits, e.g. A000100000001"),
  issueDate: IsoDate,
  dueDate: IsoDate.optional(),
  amount: z.string().regex(/^\d{1,13}(\.\d{1,2})?$/, "Enter an amount, e.g. 120.50"),
});
const WithId = z.object({ id: z.string() });

export async function createSettlement(_: ActionState, form: FormData): Promise<ActionState> {
  const input = Header.safeParse(formObject(form));
  if (!input.success) return invalid(input.error);
  let id = "";
  const failure = await attempt(async () => {
    ({ id } = await asUser("api/v1/settlement/create", { method: "POST", json: { ...input.data, invoices: [] }, schema: WithId }));
  });
  if (failure) return failure;
  redirect(`/settlements/${id}`);
}

/**
 * PUT /settlement/update replaces all invoices with the ones sent (losing their files), so the
 * header is only editable while there are none. Checked here against current data, not the form.
 */
export async function updateSettlement(settlementId: string, _: ActionState, form: FormData): Promise<ActionState> {
  const id = Uuid.parse(settlementId);
  const input = Header.safeParse(formObject(form));
  if (!input.success) return invalid(input.error);
  const failure = await attempt(async () => {
    const current = await asUser(`api/v1/settlement/${id}`, { schema: z.object({ invoices: z.array(z.unknown()).nullish() }) });
    if (current.invoices?.length) throw new GatewayError(409, "A settlement with invoices can't be edited. Remove its invoices first.");
    await asUser("api/v1/settlement/update", { method: "PUT", json: { id, ...input.data, invoices: [] } });
  });
  if (failure) return failure;
  revalidatePath(`/settlements/${id}`);
  return { ok: "Saved." };
}

export async function addInvoice(settlementId: string, _: ActionState, form: FormData): Promise<ActionState> {
  const id = Uuid.parse(settlementId);
  const input = Invoice.safeParse(formObject(form));
  if (!input.success) return invalid(input.error);
  const file = form.get("file");
  const problem = file instanceof File ? uploadProblem(file) : "Choose a file";
  if (problem) return { error: "Please correct the highlighted fields.", fields: { file: problem } };

  const body = new FormData();
  body.set("settlementId", id);
  for (const [k, v] of Object.entries(input.data)) if (v) body.set(k, v);
  body.set("file", file as File, (file as File).name);
  const failure = await attempt(() => asUser("api/v1/settlement/invoice/add", { method: "POST", form: body }));
  if (failure) return failure;
  revalidatePath(`/settlements/${id}`);
  return { ok: "Invoice added." };
}

export async function removeInvoice(settlementId: string, invoiceId: string, _: ActionState): Promise<ActionState> {
  const json = { settlementId: Uuid.parse(settlementId), invoiceId: Uuid.parse(invoiceId) };
  const failure = await attempt(() => asUser("api/v1/settlement/invoice/remove", { method: "DELETE", json }));
  if (failure) return failure;
  revalidatePath(`/settlements/${json.settlementId}`);
  return { ok: "Invoice removed." };
}

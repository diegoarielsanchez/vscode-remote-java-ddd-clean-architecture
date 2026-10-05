import { z } from "zod";
import { ApiError, http, seg } from "../../core/http";

// Amounts are Java BigDecimal: kept as strings so JavaScript never rounds them.
const Money = z.union([z.number(), z.string()]).transform(String);

export const InvoiceSchema = z.object({
  id: z.string(),
  invoiceNumber: z.string(),
  issueDate: z.string(),
  dueDate: z.string().nullish(),
  amount: Money,
  status: z.string().nullish(),
  fileName: z.string().nullish(),
  contentType: z.string().nullish(),
  sizeInBytes: z.number().nullish(),
  sha256Hash: z.string().nullish(),
});
export type Invoice = z.infer<typeof InvoiceSchema>;

export const SettlementSchema = z.object({
  id: z.string(),
  description: z.string(),
  settlementDate: z.string(),
  status: z.string().nullish(),
  totalAmount: Money.default("0"),
  invoices: z.array(InvoiceSchema).nullish().transform((i) => i ?? []),
  medicalSalesRepId: z.string(),
});
export type Settlement = z.infer<typeof SettlementSchema>;

export type SettlementHeader = {
  id?: string;
  description: string;
  settlementDate: string; // yyyy-MM-dd
  medicalSalesRepId: string;
};

export type InvoiceDraft = {
  invoiceNumber: string;
  issueDate: string; // yyyy-MM-dd
  dueDate?: string;
  amount: string; // decimal string
  file: File;
};

/**
 * Backend caveat: PUT /settlement/update rebuilds the settlement with *only* the invoices in the
 * request (new ids, no file metadata). Sending none deletes the existing ones; resending them
 * loses their files. So the header can only be edited while there are no invoices.
 */
export const canEditHeader = (s: Settlement) => s.invoices.length === 0;

/** settlement-service via the gateway route /api/v1/settlement/... */
export const settlementRepository = {
  list: (page: number, pageSize: number, signal?: AbortSignal) =>
    http("api/v1/settlement/list", { method: "POST", query: { page, pageSize }, schema: z.array(SettlementSchema), signal }),

  get: (id: string, signal?: AbortSignal) => http(`api/v1/settlement/${seg(id)}`, { schema: SettlementSchema, signal }),

  /** Creates an empty settlement; invoices (each with its file) are added afterwards. */
  create: (h: SettlementHeader) =>
    http("api/v1/settlement/create", {
      method: "POST",
      schema: SettlementSchema,
      json: { description: h.description, settlementDate: h.settlementDate, medicalSalesRepId: h.medicalSalesRepId, invoices: [] },
    }),

  async updateHeader(h: SettlementHeader & { id: string }) {
    const current = await settlementRepository.get(h.id);
    if (!canEditHeader(current)) {
      throw new ApiError(409, "A settlement with invoices can't be edited. Remove its invoices first.");
    }
    return http("api/v1/settlement/update", {
      method: "PUT",
      schema: SettlementSchema,
      json: { id: h.id, description: h.description, settlementDate: h.settlementDate, medicalSalesRepId: h.medicalSalesRepId, invoices: [] },
    });
  },

  /** Multipart: plain form fields + a "file" part (max 10 MB; .pdf .xlsx .docx .txt). */
  addInvoice(settlementId: string, d: InvoiceDraft) {
    const form = new FormData();
    form.append("settlementId", settlementId);
    form.append("invoiceNumber", d.invoiceNumber);
    form.append("issueDate", d.issueDate);
    if (d.dueDate) form.append("dueDate", d.dueDate);
    form.append("amount", d.amount);
    form.append("file", d.file, d.file.name);
    return http("api/v1/settlement/invoice/add", { method: "POST", form, schema: InvoiceSchema });
  },

  /** DELETE with a JSON body. */
  removeInvoice: (settlementId: string, invoiceId: string) =>
    http("api/v1/settlement/invoice/remove", { method: "DELETE", json: { settlementId, invoiceId }, schema: SettlementSchema }),
};

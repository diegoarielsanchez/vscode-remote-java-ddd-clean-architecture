import { z } from "zod";
import { emptyOnNotFound, http, seg } from "../../core/http";

// ── Schemas: mirror visit-domain usecases/dtos ───────────────────────────────────

const AttachmentSchema = z.object({
  fileName: z.string(),
  storageReference: z.string().nullish(),
  sha256Hash: z.string().nullish(),
});

/** VisitOutputDTO. `visitDate` is a LocalDateTime (no offset) in responses. */
export const VisitSchema = z.object({
  id: z.string(),
  visitDate: z.string(),
  healthCareProfId: z.string().nullish(),
  medicalSalesRepId: z.string().nullish(),
  visitSiteId: z.string().nullish(),
  visitComments: z.string().nullish(),
  productPromoAttachments: z.array(AttachmentSchema).nullish().transform((a) => a ?? []),
});
export type Visit = z.infer<typeof VisitSchema>;

export const VisitPlanSchema = z.object({
  id: z.string(),
  visitDateTime: z.string(),
  healthCareProfId: z.string().nullish(),
  medicalSalesRepId: z.string().nullish(),
  visitSiteId: z.string().nullish(),
  visitComments: z.string().nullish(),
  active: z.boolean().default(true),
});
export type VisitPlan = z.infer<typeof VisitPlanSchema>;

const UploadResultSchema = z.object({ visitId: z.string(), attachments: z.array(AttachmentSchema).default([]) });

/** Form fields use the backend property names so server validation errors map 1:1. */
export type VisitDraft = {
  id?: string;
  visitDate: string; // yyyy-MM-dd
  healthCareProfId: string;
  medicalSalesRepId: string;
  visitSiteId: string;
  visitComments: string;
};

export type VisitPlanDraft = {
  id?: string;
  visitDateTime: string; // yyyy-MM-ddTHH:mm:ss, must be in the future
  healthCareProfId: string;
  medicalSalesRepId: string;
  visitSiteId: string;
  visitComments: string;
};

// ── Repositories: gateway routes /api/v1/visit/** and /api/v1/visitplan/** ──────

export const visitRepository = {
  /** POST with query params and no body; pages start at 1. */
  list: (page: number, pageSize: number, signal?: AbortSignal) =>
    http("api/v1/visit/list", { method: "POST", query: { page, pageSize }, schema: z.array(VisitSchema), signal })
      .catch(emptyOnNotFound<Visit>),

  get: (id: string, signal?: AbortSignal) => http(`api/v1/visit/${seg(id)}`, { schema: VisitSchema, signal }),

  save: (d: VisitDraft) =>
    http(d.id ? "api/v1/visit/update" : "api/v1/visit/create", {
      method: d.id ? "PUT" : "POST",
      schema: VisitSchema,
      json: { ...d, visitComments: d.visitComments.trim() || null },
    }),

  /** Part name "files", repeatable. */
  uploadAttachments: (visitId: string, files: File[]) => {
    const form = new FormData();
    files.forEach((f) => form.append("files", f, f.name));
    return http(`api/v1/visit/${seg(visitId)}/attachments`, { method: "POST", form, schema: UploadResultSchema });
  },
};

export const visitPlanRepository = {
  list: (page: number, pageSize: number, signal?: AbortSignal) =>
    http("api/v1/visitplan/list", { method: "POST", query: { page, pageSize }, schema: z.array(VisitPlanSchema), signal })
      .catch(emptyOnNotFound<VisitPlan>),

  get: (id: string, signal?: AbortSignal) => http(`api/v1/visitplan/${seg(id)}`, { schema: VisitPlanSchema, signal }),

  save: (d: VisitPlanDraft) =>
    http(d.id ? "api/v1/visitplan/update" : "api/v1/visitplan/create", {
      method: d.id ? "PUT" : "POST",
      schema: VisitPlanSchema,
      json: { ...d, visitComments: d.visitComments.trim() || null },
    }),
};

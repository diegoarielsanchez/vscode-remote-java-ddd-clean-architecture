import { z } from "zod";
import { emptyOnNotFound, http, seg } from "../../core/http";
import type { PickerItem } from "../../core/ui/Picker";

const MsrSchema = z.object({
  id: z.string(),
  name: z.string(),
  surname: z.string(),
  email: z.string().nullish(),
  active: z.boolean().nullish(),
});

export type Msr = { id: string; fullName: string; email: string; active: boolean };

const toMsr = (d: z.infer<typeof MsrSchema>): Msr => ({
  id: d.id,
  fullName: `${d.name} ${d.surname}`,
  email: d.email ?? "",
  active: d.active ?? true,
});

export const toMsrPickerItem = (m: Msr): PickerItem => ({ id: m.id, title: m.fullName, subtitle: m.email || undefined });

/** medical-sales-rep-service via the gateway (read-only here). Same search caveats as HCPs. */
export const msrRepository = {
  all: (signal?: AbortSignal): Promise<Msr[]> =>
    http("api/v1/medicalsalesrep/list", {
      method: "POST",
      query: { firstName: "", lastName: "", page: 1, pageSize: 100 },
      schema: z.array(MsrSchema),
      signal,
    })
      .then((list) => list.map(toMsr))
      .catch(emptyOnNotFound<Msr>),

  get: (id: string, signal?: AbortSignal): Promise<Msr> =>
    http(`api/v1/medicalsalesrep/${seg(id)}`, { schema: MsrSchema, signal }).then(toMsr),
};

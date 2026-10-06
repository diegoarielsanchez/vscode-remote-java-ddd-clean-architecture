import { z } from "zod";
import { emptyOnNotFound, http, seg } from "../../core/http";
import type { PickerItem } from "../../core/ui/Picker";

const HcpSchema = z.object({
  id: z.string(),
  name: z.string(),
  surname: z.string(),
  email: z.string().nullish(),
  active: z.boolean().nullish(),
  specialties: z.array(z.string()).default([]), // specialty display names
});

export type Hcp = {
  id: string;
  fullName: string;
  email: string;
  active: boolean;
  specialties: string[];
};

const toHcp = (d: z.infer<typeof HcpSchema>): Hcp => ({
  id: d.id,
  fullName: `${d.name} ${d.surname}`,
  email: d.email ?? "",
  active: d.active ?? true,
  specialties: d.specialties,
});

export const toHcpPickerItem = (h: Hcp): PickerItem => ({
  id: h.id,
  title: h.fullName,
  subtitle: h.email || undefined,
  tags: h.specialties,
});

/** healthcare-prof-service via the gateway (read-only here). */
export const hcpRepository = {
  /**
   * The backend's name filter is exact-match, and an empty filter returns everyone unpaginated,
   * so the directory is fetched once and searched on the device.
   */
  all: (signal?: AbortSignal): Promise<Hcp[]> =>
    http("api/v1/healthcareprof/list", {
      method: "POST",
      query: { firstName: "", lastName: "", page: 1, pageSize: 100 },
      schema: z.array(HcpSchema),
      signal,
    })
      .then((list) => list.map(toHcp))
      .catch(emptyOnNotFound<Hcp>),

  get: (id: string, signal?: AbortSignal): Promise<Hcp> =>
    http(`api/v1/healthcareprof/${seg(id)}`, { schema: HcpSchema, signal }).then(toHcp),
};

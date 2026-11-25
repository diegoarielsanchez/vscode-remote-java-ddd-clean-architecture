import "server-only";
import { z } from "zod";
import { cache } from "react";
import { seg } from "@/server/gateway";
import { emptyOnNotFound } from "@/server/gateway-error";
import { asUser, load } from "@/server/session";

const Person = z.object({
  id: z.string(),
  name: z.string(),
  surname: z.string(),
  email: z.string().nullish(),
  active: z.boolean().nullish().transform((a) => a ?? true),
  specialties: z.array(z.string()).nullish().transform((s) => s ?? []), // display names
});
export type Person = z.infer<typeof Person>;
export type PersonKind = "hcp" | "msr";
export const RESOURCE = { hcp: "healthcareprof", msr: "medicalsalesrep" } as const;
export const fullName = (p: Person) => `${p.name} ${p.surname}`;

/**
 * The backend's name filter is exact-match and its unfiltered list isn't paginated, so the BFF
 * reads everyone and filters here: the browser only receives the page it shows.
 */
export const listPeople = cache((kind: PersonKind) =>
  load(asUser(`api/v1/${RESOURCE[kind]}/list`, { method: "POST", query: { page: 1, pageSize: 100 }, schema: z.array(Person) })
    .catch(emptyOnNotFound)));

export const getPerson = (kind: PersonKind, id: string) => load(asUser(`api/v1/${RESOURCE[kind]}/${seg(id)}`, { schema: Person }));

export async function msrNames(): Promise<(id: string) => string> {
  // A label only: if the MSR service is down or limited, pages still work and show the id.
  const people = await asUser("api/v1/medicalsalesrep/list", { method: "POST", query: { page: 1, pageSize: 100 }, schema: z.array(Person) })
    .catch(() => [] as Person[]);
  const byId = new Map(people.map((p) => [p.id, fullName(p)]));
  return (id) => byId.get(id) ?? id;
}

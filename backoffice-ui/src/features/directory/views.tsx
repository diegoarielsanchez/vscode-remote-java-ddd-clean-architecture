import Link from "next/link";
import { Suspense } from "react";
import { AuditTrail } from "@/features/audit/audit";
import { getSpecialties } from "@/server/catalog";
import { isAdmin, requireSession } from "@/server/session";
import { Badge, Card, Facts, PageHeader, Pager, Skeleton, Table } from "@/ui/components";
import { ActionForm, Check, Field, TextArea } from "@/ui/form";
import { pageNumber, slicePage } from "@/ui/format";
import { createPerson, setPersonActive, updatePerson } from "./actions";
import { RESOURCE, fullName, getPerson, listPeople, type Person, type PersonKind } from "./api";

export const TITLE = { hcp: "Healthcare professionals", msr: "Medical sales reps" } as const;

export async function PeopleList({ kind, q, page: pageParam }: { kind: PersonKind; q?: string; page?: string }) {
  await requireSession();
  const term = (q ?? "").trim().toLowerCase();
  const page = pageNumber(pageParam);
  const matches = (await listPeople(kind))
    .filter((p) => !term || `${fullName(p)} ${p.email ?? ""}`.toLowerCase().includes(term))
    .sort((a, b) => fullName(a).localeCompare(fullName(b)));
  const { items, hasNext } = slicePage(matches, page);
  return (
    <>
      <PageHeader title={TITLE[kind]}><Link className="btn-primary" href={`/${kind}s/new`}>New</Link></PageHeader>
      <form className="mb-4 flex gap-2" role="search">
        <input className="field max-w-sm" type="search" name="q" defaultValue={q} aria-label="Search by name or e-mail" placeholder="Name or e-mail" />
        <button className="btn-secondary">Search</button>
      </form>
      <Table head={kind === "hcp" ? ["Name", "E-mail", "Specialties", "Status"] : ["Name", "E-mail", "Status"]} empty="Nobody found.">
        {items.map((p) => (
          <tr key={p.id}>
            <td className="cell"><Link prefetch={false} className="link" href={`/${kind}s/${p.id}`}>{fullName(p)}</Link></td>
            <td className="cell">{p.email}</td>
            {kind === "hcp" && <td className="cell">{p.specialties.join(", ")}</td>}
            <td className="cell"><Badge code={p.active ? "ACTIVE" : "INACTIVE"} /></td>
          </tr>
        ))}
      </Table>
      <Pager page={page} hasNext={hasNext} href={(n) => `/${kind}s?${new URLSearchParams({ ...(q ? { q } : {}), page: String(n) })}`} />
    </>
  );
}

async function PersonFields({ kind, person }: { kind: PersonKind; person?: Person }) {
  // Specialties come from the shared ISR data cache: the same list for everyone, no personal data.
  const specialties = kind === "hcp" ? await getSpecialties() : [];
  return (
    <>
      <Field name="name" label="First name" required maxLength={100} defaultValue={person?.name} />
      <Field name="surname" label="Last name" required maxLength={100} defaultValue={person?.surname} />
      <Field name="email" label="E-mail" type="email" required defaultValue={person?.email ?? ""} />
      {kind === "hcp" && (
        <fieldset>
          <legend className="label">Specialties</legend>
          <div className="grid gap-1 sm:grid-cols-2">
            {specialties.map((s) => <Check key={s.code} name="specialties" value={s.code} label={s.name} defaultChecked={person?.specialties.includes(s.name)} />)}
          </div>
        </fieldset>
      )}
    </>
  );
}

export async function NewPerson({ kind }: { kind: PersonKind }) {
  await requireSession();
  return (
    <>
      <PageHeader title={kind === "hcp" ? "New healthcare professional" : "New medical sales rep"} />
      <Card><ActionForm action={createPerson.bind(null, kind)} submit="Create"><PersonFields kind={kind} /></ActionForm></Card>
    </>
  );
}

export async function PersonDetail({ kind, id }: { kind: PersonKind; id: string }) {
  const session = await requireSession();
  const person = await getPerson(kind, id);
  return (
    <div className="grid gap-6">
      <PageHeader title={fullName(person)}><Badge code={person.active ? "ACTIVE" : "INACTIVE"} /></PageHeader>
      {isAdmin(session) && (
        <Card title="Status">
          <ActionForm action={setPersonActive.bind(null, kind, id, !person.active)} submit={person.active ? "Deactivate" : "Activate"}
            tone={person.active ? "danger" : "primary"} confirm={person.active ? `Deactivate ${fullName(person)}?` : undefined} />
        </Card>
      )}
      <Card title="Details">
        <Facts items={[["Id", id]]} />
        <ActionForm action={updatePerson.bind(null, kind, id)} submit="Save" className="mt-4 grid gap-4">
          <PersonFields kind={kind} person={person} />
          <TextArea name="reason" label="Reason for the change" required maxLength={500} hint="Kept in the audit trail." />
        </ActionForm>
      </Card>
      {isAdmin(session) && <Suspense fallback={<Skeleton label="Loading audit trail…" />}><AuditTrail resource={RESOURCE[kind]} id={id} /></Suspense>}
    </div>
  );
}

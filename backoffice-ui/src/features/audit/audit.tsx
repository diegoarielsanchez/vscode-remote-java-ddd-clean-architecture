import { z } from "zod";
import { asUser, load } from "@/server/session";
import { seg } from "@/server/gateway";
import { Card } from "@/ui/components";
import { formatDateTime } from "@/ui/format";

const Entry = z.object({
  version: z.number(),
  eventType: z.string(),
  occurredAt: z.string(),
  actor: z.string().nullish(),
  reason: z.string().nullish(),
  changes: z.array(z.object({ field: z.string(), from: z.unknown(), to: z.unknown() })).nullish().catch(null),
});

const text = (v: unknown) => (v == null ? "—" : typeof v === "object" ? JSON.stringify(v) : String(v));

/** Admins only (the backend enforces it). Render inside <Suspense> so it streams after the page. */
export async function AuditTrail({ resource, id }: { resource: "orders" | "healthcareprof" | "medicalsalesrep" | "products"; id: string }) {
  const entries = await load(asUser(`api/v1/${resource}/${seg(id)}/history`, { schema: z.array(Entry) }));
  return (
    <Card title="Audit trail">
      {entries.length === 0 ? <p className="text-slate-600">No changes recorded.</p> : (
        <ol className="grid gap-3">
          {entries.map((e, i) => (
            <li key={i} className="border-l-2 border-slate-300 pl-3 text-sm">
              <p><strong>{e.eventType}</strong> · {formatDateTime(e.occurredAt)} · {e.actor ?? "system"}</p>
              {e.reason && <p>Reason: {e.reason}</p>}
              {e.changes?.map((c) => <p key={c.field} className="break-words text-slate-600">{c.field}: {text(c.from)} → {text(c.to)}</p>)}
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}

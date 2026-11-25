import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";
import { decideLot } from "@/features/products/actions";
import { getProduct, listLots, lotRecipients } from "@/features/products/api";
import { isUuid } from "@/features/schemas";
import { isAdmin, requireSession } from "@/server/session";
import { Badge, Card, PageHeader, Skeleton, Table } from "@/ui/components";
import { ActionForm, Select, TextArea } from "@/ui/form";
import { formatDate } from "@/ui/format";

export const metadata = { title: "Lots" };

/** SSR, never cached: a stale quarantine status would be a compliance risk. */
export default async function LotsPage({ params, searchParams }: PageProps<"/products/[id]/lots">) {
  const session = await requireSession();
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const { batch } = await searchParams;
  const [product, lots] = await Promise.all([getProduct(id), listLots(id)]);
  const admin = isAdmin(session);
  return (
    <div className="grid gap-6">
      <PageHeader title={`Lots of ${product.name}`} />
      <Table head={["Batch", "Expires", "Units", "Status", admin ? "Quality" : ""]} empty="No lots.">
        {lots.map((lot) => (
          <tr key={lot.batchNumber ?? ""}>
            <td className="cell">{lot.batchNumber ?? "UNBATCHED"}
              {admin && lot.batchNumber && <Link prefetch={false} className="link block text-sm" href={`/products/${id}/lots?${new URLSearchParams({ batch: lot.batchNumber })}`}>Who received it?</Link>}
            </td>
            <td className="cell">{formatDate(lot.expirationDate)}{lot.expired && <span className="badge tone-bad ml-2">Expired</span>}</td>
            <td className="cell">{lot.quantity}</td>
            <td className="cell"><Badge code={lot.status} /></td>
            <td className="cell">
              {admin && lot.status !== "REJECTED" && (
                <details>
                  <summary className="cursor-pointer text-brand">Decide</summary>
                  <ActionForm action={decideLot.bind(null, id, lot.batchNumber ?? null)} submit="Record" className="mt-2 grid gap-3">
                    <Select name="decision" label="Decision" options={lot.status === "QUARANTINED"
                      ? [{ value: "lift-quarantine", label: "Lift quarantine" }, { value: "reject", label: "Reject (final)" }]
                      : [{ value: "quarantine", label: "Quarantine" }]} />
                    <TextArea name="reason" label="Reason" required maxLength={500} />
                  </ActionForm>
                </details>
              )}
            </td>
          </tr>
        ))}
      </Table>
      {admin && typeof batch === "string" && <Suspense fallback={<Skeleton />}><Recipients id={id} batch={batch} /></Suspense>}
    </div>
  );
}

async function Recipients({ id, batch }: { id: string; batch: string }) {
  const recipients = await lotRecipients(id, batch);
  return (
    <Card title={`Orders that received batch ${batch}`}>
      <Table head={["Order", "Reservation", "Units"]} empty="No orders received this batch.">
        {recipients.map((r) => (
          <tr key={r.orderId}>
            <td className="cell"><Link prefetch={false} className="link" href={`/orders/${r.orderId}`}>{r.orderId.slice(0, 8)}</Link></td>
            <td className="cell"><Badge code={r.reservationStatus} /></td><td className="cell">{r.quantity}</td>
          </tr>
        ))}
      </Table>
    </Card>
  );
}

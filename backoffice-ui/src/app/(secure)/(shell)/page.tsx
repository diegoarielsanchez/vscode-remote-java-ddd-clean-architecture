import Link from "next/link";
import { Suspense } from "react";
import { listOrders } from "@/features/orders/api";
import { listSettlements } from "@/features/settlements/api";
import { requireSession } from "@/server/session";
import { Badge, Card, PageHeader, Skeleton } from "@/ui/components";
import { formatDate, formatDateTime, formatMoney, humanize } from "@/ui/format";

export const metadata = { title: "Dashboard" };

/** SSR with streaming: the page is sent at once and each card follows when its service answers. */
export default async function Dashboard() {
  await requireSession();
  return (
    <>
      <PageHeader title="Dashboard" />
      <div className="grid gap-6 lg:grid-cols-2">
        <Suspense fallback={<Skeleton label="Loading orders…" />}><Orders /></Suspense>
        <Suspense fallback={<Skeleton label="Loading settlements…" />}><Settlements /></Suspense>
      </div>
    </>
  );
}

async function Orders() {
  const orders = await listOrders();
  const counts = Object.entries(Object.groupBy(orders, (o) => o.status));
  const quarantined = orders.filter((o) => o.status === "DELIVERY_QUARANTINED");
  return (
    <Card title="Orders">
      <ul className="mb-4 flex flex-wrap gap-2">
        {counts.map(([status, list]) => <li key={status} className="rounded-lg bg-slate-100 px-3 py-1 text-sm">{humanize(status)}: <strong>{list!.length}</strong></li>)}
      </ul>
      <h3 className="mb-2 font-medium">Deliveries waiting for Quality</h3>
      {quarantined.length === 0 ? <p className="text-slate-600">None.</p> : (
        <ul className="grid gap-1">
          {quarantined.slice(0, 5).map((o) => (
            <li key={o.id}><Link prefetch={false} className="link" href={`/orders/${o.id}`}>{o.id.slice(0, 8)}</Link> · {formatDateTime(o.createdAt)}</li>
          ))}
        </ul>
      )}
    </Card>
  );
}

async function Settlements() {
  const settlements = await listSettlements(1, 5);
  return (
    <Card title="Settlements">
      {settlements.length === 0 ? <p className="text-slate-600">None yet.</p> : (
        <ul className="grid gap-2">
          {settlements.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center gap-2">
              <Link prefetch={false} className="link" href={`/settlements/${s.id}`}>{s.description}</Link>
              <span className="text-slate-600">{formatDate(s.settlementDate)} · {formatMoney(s.totalAmount)}</span><Badge code={s.status} />
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

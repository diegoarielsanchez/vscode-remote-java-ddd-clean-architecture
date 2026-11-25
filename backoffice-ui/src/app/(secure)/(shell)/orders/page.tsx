import Link from "next/link";
import { msrNames } from "@/features/directory/api";
import { listOrders } from "@/features/orders/api";
import { requireSession } from "@/server/session";
import { Badge, PageHeader, Pager, Table } from "@/ui/components";
import { formatDateTime, formatMoney, humanize, pageNumber, slicePage } from "@/ui/format";

export const metadata = { title: "Orders" };

const STATUSES = ["AWAITING_STOCK", "STOCK_REJECTED", "PENDING_APPROVAL", "APPROVED", "REJECTED", "DELIVERED", "DELIVERY_QUARANTINED", "DELIVERY_REJECTED"];

export default async function OrdersPage({ searchParams }: PageProps<"/orders">) {
  await requireSession();
  const query = await searchParams;
  const status = STATUSES.find((s) => s === query.status) ?? "";
  const page = pageNumber(query.page);
  const [orders, msrName] = await Promise.all([listOrders(), msrNames()]);
  const { items, hasNext } = slicePage(orders.filter((o) => !status || o.status === status), page);
  return (
    <>
      <PageHeader title="Orders" />
      <form className="mb-4 flex gap-2">
        <select name="status" defaultValue={status} className="field max-w-xs" aria-label="Status">
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{humanize(s)}</option>)}
        </select>
        <button className="btn-secondary">Filter</button>
      </form>
      <Table head={["Order", "Sales rep", "Created", "Total", "Status"]} empty="No orders.">
        {items.map((o) => (
          <tr key={o.id}>
            <td className="cell"><Link prefetch={false} className="link" href={`/orders/${o.id}`}>{o.id.slice(0, 8)}</Link></td>
            <td className="cell">{msrName(o.medicalSalesRepId)}</td>
            <td className="cell">{formatDateTime(o.createdAt)}</td>
            <td className="cell">{formatMoney(o.totalAmount)}</td>
            <td className="cell"><Badge code={o.status} /></td>
          </tr>
        ))}
      </Table>
      <Pager page={page} hasNext={hasNext} href={(n) => `/orders?${new URLSearchParams({ ...(status ? { status } : {}), page: String(n) })}`} />
    </>
  );
}

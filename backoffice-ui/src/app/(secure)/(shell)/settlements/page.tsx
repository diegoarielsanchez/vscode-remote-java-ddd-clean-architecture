import Link from "next/link";
import { msrNames } from "@/features/directory/api";
import { listSettlements } from "@/features/settlements/api";
import { requireSession } from "@/server/session";
import { Badge, PageHeader, Pager, Table } from "@/ui/components";
import { PAGE_SIZE, formatDate, formatMoney, pageNumber } from "@/ui/format";

export const metadata = { title: "Settlements" };

export default async function SettlementsPage({ searchParams }: PageProps<"/settlements">) {
  await requireSession();
  const page = pageNumber((await searchParams).page);
  const [settlements, msrName] = await Promise.all([listSettlements(page, PAGE_SIZE), msrNames()]);
  return (
    <>
      <PageHeader title="Settlements"><Link className="btn-primary" href="/settlements/new">New</Link></PageHeader>
      <Table head={["Description", "Date", "Sales rep", "Invoices", "Total", "Status"]} empty="No settlements.">
        {settlements.map((s) => (
          <tr key={s.id}>
            <td className="cell"><Link prefetch={false} className="link" href={`/settlements/${s.id}`}>{s.description}</Link></td>
            <td className="cell">{formatDate(s.settlementDate)}</td><td className="cell">{msrName(s.medicalSalesRepId)}</td>
            <td className="cell">{s.invoices.length}</td><td className="cell">{formatMoney(s.totalAmount)}</td>
            <td className="cell"><Badge code={s.status} /></td>
          </tr>
        ))}
      </Table>
      <Pager page={page} hasNext={settlements.length === PAGE_SIZE} href={(n) => `/settlements?page=${n}`} />
    </>
  );
}

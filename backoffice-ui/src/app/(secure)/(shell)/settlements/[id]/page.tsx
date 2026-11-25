import { notFound } from "next/navigation";
import { msrNames } from "@/features/directory/api";
import { isUuid } from "@/features/schemas";
import { HeaderFields } from "@/features/settlements/HeaderFields";
import { addInvoice, removeInvoice, updateSettlement } from "@/features/settlements/actions";
import { getSettlement } from "@/features/settlements/api";
import { ALLOWED_EXTENSIONS } from "@/features/settlements/upload";
import { requireSession } from "@/server/session";
import { Badge, Card, Facts, PageHeader, Table } from "@/ui/components";
import { ActionForm, Field } from "@/ui/form";
import { formatDate, formatMoney } from "@/ui/format";

export const metadata = { title: "Settlement" };

export default async function SettlementPage({ params }: PageProps<"/settlements/[id]">) {
  await requireSession();
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const [settlement, msrName] = await Promise.all([getSettlement(id), msrNames()]);
  const open = settlement.status !== "CLOSED";
  return (
    <div className="grid gap-6">
      <PageHeader title={settlement.description}><Badge code={settlement.status} /></PageHeader>
      <Card>
        <Facts items={[
          ["Date", formatDate(settlement.settlementDate)], ["Sales rep", msrName(settlement.medicalSalesRepId)], ["Total", formatMoney(settlement.totalAmount)],
        ]} />
      </Card>
      <Table head={["Invoice", "Issued", "Due", "Amount", "File", ""]} empty="No invoices.">
        {settlement.invoices.map((i) => (
          <tr key={i.id}>
            <td className="cell">{i.invoiceNumber}</td><td className="cell">{formatDate(i.issueDate)}</td><td className="cell">{formatDate(i.dueDate)}</td>
            <td className="cell">{formatMoney(i.amount)}</td>
            <td className="cell">{i.fileName ?? "—"}{i.sha256Hash && <span className="block font-mono text-xs text-slate-600" title={i.sha256Hash}>SHA-256 {i.sha256Hash.slice(0, 12)}…</span>}</td>
            <td className="cell">{open && <ActionForm action={removeInvoice.bind(null, id, i.id)} submit="Remove" tone="secondary" confirm={`Remove invoice ${i.invoiceNumber} and its file?`} />}</td>
          </tr>
        ))}
      </Table>
      {open && (
        <Card title="Add an invoice">
          <ActionForm action={addInvoice.bind(null, id)} submit="Add invoice">
            <div className="grid gap-4 sm:grid-cols-2">
              <Field name="invoiceNumber" label="Invoice number" required hint="One letter and 12 digits" maxLength={13} />
              <Field name="amount" label="Amount" inputMode="decimal" required />
              <Field name="issueDate" label="Issue date" type="date" required />
              <Field name="dueDate" label="Due date (optional)" type="date" />
            </div>
            <Field name="file" label="File" type="file" accept={ALLOWED_EXTENSIONS.join(",")} required hint="Up to 10 MB" />
          </ActionForm>
        </Card>
      )}
      {open && settlement.invoices.length === 0 && (
        <Card title="Edit"><ActionForm action={updateSettlement.bind(null, id)} submit="Save"><HeaderFields value={settlement} /></ActionForm></Card>
      )}
    </div>
  );
}

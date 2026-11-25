import { notFound } from "next/navigation";
import { Suspense } from "react";
import { AuditTrail } from "@/features/audit/audit";
import { msrNames } from "@/features/directory/api";
import { LiveStatus } from "@/features/orders/LiveStatus";
import { decideDelivery } from "@/features/orders/actions";
import { getOrder } from "@/features/orders/api";
import { isUuid } from "@/features/schemas";
import { isChanging } from "@/server/order-stream";
import { isAdmin, requireSession } from "@/server/session";
import { Badge, Card, Facts, PageHeader, Skeleton, Table } from "@/ui/components";
import { ActionForm, TextArea } from "@/ui/form";
import { formatDateTime, formatMoney } from "@/ui/format";

export const metadata = { title: "Order" };

export default async function OrderPage({ params }: PageProps<"/orders/[id]">) {
  const session = await requireSession();
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const [order, msrName] = await Promise.all([getOrder(id), msrNames()]);
  const check = order.deliveryCheck;
  return (
    <div className="grid gap-6">
      <PageHeader title={`Order ${id.slice(0, 8)}`}><Badge code={order.status} /></PageHeader>
      {isChanging(order.status) && <LiveStatus orderId={id} status={order.status} />}
      <Card>
        <Facts items={[
          ["Sales rep", msrName(order.medicalSalesRepId)],
          ["Created", formatDateTime(order.createdAt)],
          ["Total", formatMoney(order.totalAmount)],
          ["Approved", order.approvedAt ? `${formatDateTime(order.approvedAt)} by ${order.approvedBy ?? "—"}` : "—"],
          ["Rejected", order.rejectionReason ? `${formatDateTime(order.rejectedAt)}: ${order.rejectionReason}` : "—"],
          ["Delivered", formatDateTime(order.deliveredAt)],
        ]} />
      </Card>
      <Table head={["Product", "Quantity", "Unit price", "Line total", "Storage"]} empty="No lines.">
        {order.lines.map((l) => (
          <tr key={l.productId}>
            <td className="cell">{l.productName ?? l.productId}</td><td className="cell">{l.quantity}</td>
            <td className="cell">{formatMoney(l.unitPrice)}</td><td className="cell">{formatMoney(l.lineTotal)}</td>
            <td className="cell">{l.storageCondition?.description ?? "—"}</td>
          </tr>
        ))}
      </Table>
      {check && (
        <Card title="Transport report">
          <Facts items={[
            ["Conditions kept", check.conditionsMaintained ? "Yes" : "No"],
            ["Temperature", check.minTemperatureC != null || check.maxTemperatureC != null ? `${check.minTemperatureC ?? "—"} to ${check.maxTemperatureC ?? "—"} °C` : "—"],
            ["Breaches", check.breaches.join("; ") || "None"],
            ["Note", check.note],
            ["Decision", check.decidedBy ? `${check.decidedBy}, ${formatDateTime(check.decidedAt)}: ${check.decisionReason}` : "—"],
          ]} />
        </Card>
      )}
      {order.status === "DELIVERY_QUARANTINED" && isAdmin(session) && (
        <div className="grid gap-6 md:grid-cols-2">
          <Card title="Release the delivery">
            <ActionForm action={decideDelivery.bind(null, id, "release")} submit="Release"><TextArea name="reason" label="Reason" required maxLength={500} /></ActionForm>
          </Card>
          <Card title="Reject the delivery">
            <ActionForm action={decideDelivery.bind(null, id, "reject")} submit="Reject" tone="danger" confirm="Reject this delivery? This is final.">
              <TextArea name="reason" label="Reason" required maxLength={500} />
            </ActionForm>
          </Card>
        </div>
      )}
      {isAdmin(session) && <Suspense fallback={<Skeleton label="Loading audit trail…" />}><AuditTrail resource="orders" id={id} /></Suspense>}
    </div>
  );
}

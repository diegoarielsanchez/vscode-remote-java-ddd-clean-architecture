import "server-only";
import { z } from "zod";
import { Money } from "@/features/schemas";
import { seg } from "@/server/gateway";
import { emptyOnNotFound } from "@/server/gateway-error";
import { asUser, load } from "@/server/session";

const Num = z.union([z.number(), z.string()]).nullish();
const DeliveryCheck = z.object({
  conditionsMaintained: z.boolean(),
  minTemperatureC: Num, maxTemperatureC: Num, maxHumidityPercent: Num,
  lightExposed: z.boolean().nullish(),
  note: z.string().nullish(),
  breaches: z.array(z.string()).nullish().transform((b) => b ?? []),
  decidedBy: z.string().nullish(), decisionReason: z.string().nullish(), decidedAt: z.string().nullish(),
});
const Order = z.object({
  id: z.string(),
  medicalSalesRepId: z.string(),
  status: z.string(),
  lines: z.array(z.object({
    productId: z.string(),
    productName: z.string().nullish(),
    quantity: z.number(),
    unitPrice: Money.nullish(),
    lineTotal: Money.nullish(),
    storageCondition: z.object({ description: z.string().nullish() }).nullish(),
  })),
  totalAmount: Money.nullish(),
  approvedBy: z.string().nullish(), rejectedBy: z.string().nullish(), rejectionReason: z.string().nullish(),
  createdAt: z.string().nullish(), approvedAt: z.string().nullish(), rejectedAt: z.string().nullish(), deliveredAt: z.string().nullish(),
  transportCheckRequired: z.boolean().default(false),
  deliveryCheck: DeliveryCheck.nullish(),
});
export type Order = z.infer<typeof Order>;

/** Newest first. The backend's unfiltered list isn't paginated or sorted, so the BFF does both. */
export async function listOrders(): Promise<Order[]> {
  const orders = await load(asUser("api/v1/orders/list", { method: "POST", query: { page: 1, pageSize: 100 }, schema: z.array(Order) })
    .catch(emptyOnNotFound));
  return orders.sort((a, b) => (b.createdAt ?? "").localeCompare(a.createdAt ?? ""));
}

export const getOrder = (id: string) => load(asUser(`api/v1/orders/${seg(id)}`, { schema: Order }));

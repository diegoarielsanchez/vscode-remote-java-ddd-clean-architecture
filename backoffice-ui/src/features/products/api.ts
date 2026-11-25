import "server-only";
import { z } from "zod";
import { Money } from "@/features/schemas";
import { seg } from "@/server/gateway";
import { emptyOnNotFound } from "@/server/gateway-error";
import { asUser, load } from "@/server/session";

const Num = z.union([z.number(), z.string()]).nullish();
/** The full product, read per request with the user's JWT. For the shared cache see server/cacheable.ts. */
export const Product = z.object({
  id: z.string(), name: z.string(), description: z.string().nullish(), price: Money, unit: z.string(),
  stock: z.number().nullish(), active: z.boolean().nullish(), gtin: z.string().nullish(), lotTracked: z.boolean(),
  storageCondition: z.object({
    minTemperatureC: Num, maxTemperatureC: Num, maxHumidityPercent: Num, protectFromLight: z.boolean().nullish(), description: z.string().nullish(),
  }).nullish(),
});
export const Live = Product.pick({ price: true, stock: true, active: true });

const Lot = z.object({
  batchNumber: z.string().nullish(), expirationDate: z.string().nullish(), quantity: z.number(), status: z.string(),
  receivedAt: z.string().nullish(), expired: z.boolean(), allocatable: z.boolean(),
});
const Recipient = z.object({ orderId: z.string(), reservationStatus: z.string(), quantity: z.number() });

export const getProduct = (id: string) => load(asUser(`api/v1/products/${seg(id)}`, { schema: Product }));
export const listLots = (id: string) => load(asUser(`api/v1/products/${seg(id)}/lots`, { schema: z.array(Lot) }).catch(emptyOnNotFound));
export const lotRecipients = (id: string, batchNumber: string) =>
  load(asUser(`api/v1/products/${seg(id)}/lots/recipients`, { query: { batchNumber }, schema: z.array(Recipient) }).catch(emptyOnNotFound));

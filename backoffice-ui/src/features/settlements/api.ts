import "server-only";
import { z } from "zod";
import { Money } from "@/features/schemas";
import { seg } from "@/server/gateway";
import { emptyOnNotFound } from "@/server/gateway-error";
import { asUser, load } from "@/server/session";

const Settlement = z.object({
  id: z.string(),
  description: z.string(),
  settlementDate: z.string(),
  status: z.string().nullish(),
  totalAmount: Money.nullish(),
  invoices: z.array(z.object({
    id: z.string(), invoiceNumber: z.string(), issueDate: z.string(), dueDate: z.string().nullish(),
    amount: Money, status: z.string().nullish(), fileName: z.string().nullish(), sha256Hash: z.string().nullish(),
  })).nullish().transform((i) => i ?? []),
  medicalSalesRepId: z.string(),
});

export const listSettlements = (page: number, pageSize: number) =>
  load(asUser("api/v1/settlement/list", { method: "POST", query: { page, pageSize }, schema: z.array(Settlement) }).catch(emptyOnNotFound));

export const getSettlement = (id: string) => load(asUser(`api/v1/settlement/${seg(id)}`, { schema: Settlement }));

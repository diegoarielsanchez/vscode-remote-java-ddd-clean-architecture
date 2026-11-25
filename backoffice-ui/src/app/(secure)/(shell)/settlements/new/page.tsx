import { HeaderFields } from "@/features/settlements/HeaderFields";
import { createSettlement } from "@/features/settlements/actions";
import { requireSession } from "@/server/session";
import { Card, PageHeader } from "@/ui/components";
import { ActionForm } from "@/ui/form";

export const metadata = { title: "New settlement" };

export default async function NewSettlementPage() {
  await requireSession();
  return (
    <>
      <PageHeader title="New settlement" />
      <Card><ActionForm action={createSettlement} submit="Create"><HeaderFields /></ActionForm></Card>
    </>
  );
}

import { forbidden } from "next/navigation";
import { ProductFields } from "@/features/products/ProductFields";
import { createProduct } from "@/features/products/actions";
import { isAdmin, requireSession } from "@/server/session";
import { Card, PageHeader } from "@/ui/components";
import { ActionForm, Check, Field } from "@/ui/form";

export const metadata = { title: "New product" };

export default async function NewProductPage() {
  if (!isAdmin(await requireSession())) forbidden();
  return (
    <>
      <PageHeader title="New product" />
      <Card>
        <ActionForm action={createProduct} submit="Create">
          <ProductFields />
          <Check name="lotTracked" label="Held in lots (medicines): needs storage conditions" defaultChecked />
          <div className="grid gap-4 sm:grid-cols-3">
            <Field name="initialStock" label="Initial stock" inputMode="numeric" defaultValue="0" />
            <Field name="batchNumber" label="Batch number" hint="Needed with initial stock" />
            <Field name="expirationDate" label="Expiration date" type="date" />
          </div>
        </ActionForm>
      </Card>
    </>
  );
}

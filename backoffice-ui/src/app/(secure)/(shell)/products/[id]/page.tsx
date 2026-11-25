import { notFound } from "next/navigation";
import Link from "next/link";
import { Suspense } from "react";
import { AuditTrail } from "@/features/audit/audit";
import { ProductFields } from "@/features/products/ProductFields";
import { setProductActive, updateProduct } from "@/features/products/actions";
import { getProduct } from "@/features/products/api";
import { isUuid } from "@/features/schemas";
import { isAdmin, requireSession } from "@/server/session";
import { Badge, Card, Facts, PageHeader, Skeleton } from "@/ui/components";
import { ActionForm, TextArea } from "@/ui/form";
import { formatMoney } from "@/ui/format";

export const metadata = { title: "Product" };

/** SSR: price, stock and status are read per request. The catalog's ISR pages hold only master data. */
export default async function ProductPage({ params }: PageProps<"/products/[id]">) {
  const session = await requireSession();
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const product = await getProduct(id);
  return (
    <div className="grid gap-6">
      <PageHeader title={product.name}>
        <Badge code={product.active === false ? "INACTIVE" : "ACTIVE"} />
        <Link prefetch={false} className="btn-secondary" href={`/products/${id}/lots`}>Lots</Link>
        <a className="btn-secondary" href={`/catalog/product/${id}`}>Catalog page</a>
      </PageHeader>
      <Card><Facts items={[["Price", formatMoney(product.price)], ["On hand", product.stock], ["GTIN", product.gtin], ["Storage", product.storageCondition?.description]]} /></Card>
      {isAdmin(session) && (
        <>
          <Card title="Status">
            <ActionForm action={setProductActive.bind(null, id, product.active === false)} submit={product.active === false ? "Activate" : "Deactivate"}
              tone={product.active === false ? "primary" : "danger"} confirm={product.active === false ? undefined : `Deactivate ${product.name}?`} />
          </Card>
          <Card title="Edit">
            <ActionForm action={updateProduct.bind(null, id)} submit="Save">
              <ProductFields product={product} />
              <TextArea name="reason" label="Reason for the change" required maxLength={500} hint="Kept in the audit trail." />
            </ActionForm>
          </Card>
          <Suspense fallback={<Skeleton label="Loading audit trail…" />}><AuditTrail resource="products" id={id} /></Suspense>
        </>
      )}
    </div>
  );
}

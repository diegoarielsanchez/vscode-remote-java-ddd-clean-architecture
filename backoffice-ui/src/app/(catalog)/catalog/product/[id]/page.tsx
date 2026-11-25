import { notFound } from "next/navigation";
import { LiveStock } from "@/features/products/LiveStock";
import { isUuid } from "@/features/schemas";
import { getCatalogProduct } from "@/server/catalog";
import { Card, Facts, PageHeader } from "@/ui/components";
import { formatDateTime } from "@/ui/format";

// ISR shell (master data) + CSR island (price, stock, status).
export const dynamic = "error";
export const revalidate = 3600;
export const metadata = { title: "Product" };
export const generateStaticParams = () => [];

export default async function CatalogProductPage({ params }: PageProps<"/catalog/product/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const { product, cachedAt } = await getCatalogProduct(id.toLowerCase());
  if (!product) notFound();
  const storage = product.storageCondition;
  return (
    <div className="grid gap-6">
      <PageHeader title={product.name}>
        <a className="btn-secondary" href={`/products/${id}`}>Manage</a>
        <a className="btn-secondary" href={`/products/${id}/lots`}>Lots</a>
      </PageHeader>
      <Card title="Live"><LiveStock productId={id} /></Card>
      <Card title="Master data">
        <Facts items={[
          ["Description", product.description], ["Unit", product.unit], ["GTIN", product.gtin],
          ["Storage", storage?.description], ["Cold chain", storage?.coldChain ? "Yes" : "No"], ["Held in lots", product.lotTracked ? "Yes" : "No"],
        ]} />
        <p className="mt-4 text-sm text-slate-600">As of {formatDateTime(cachedAt)}.</p>
      </Card>
    </div>
  );
}

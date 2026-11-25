import Link from "next/link";
import { notFound } from "next/navigation";
import { getCatalog } from "@/server/catalog";
import { PageHeader, Pager, Table } from "@/ui/components";
import { formatDateTime, slicePage } from "@/ui/format";

// ISR. "error" makes reading cookies or headers here a failure: no per-user data can slip in.
export const dynamic = "error";
export const revalidate = 3600;
export const metadata = { title: "Catalog" };

/** Built on first visit: the gateway isn't reachable during `next build`. */
export const generateStaticParams = () => [];

export default async function CatalogPage({ params }: PageProps<"/catalog/[page]">) {
  const { page: param } = await params;
  if (!/^[1-9]\d{0,4}$/.test(param)) notFound();
  const page = Number(param);
  const { products, cachedAt } = await getCatalog();
  const { items, hasNext } = slicePage(products, page);
  if (page > 1 && items.length === 0) notFound();
  return (
    <>
      <PageHeader title="Product catalog"><a className="btn-primary" href="/products/new">New</a></PageHeader>
      <Table head={["Product", "GTIN", "Unit", "Storage"]} empty="No products.">
        {items.map((p) => (
          <tr key={p.id}>
            <td className="cell"><Link className="link" href={`/catalog/product/${p.id}`} prefetch={false}>{p.name}</Link></td>
            <td className="cell">{p.gtin ?? "—"}</td><td className="cell">{p.unit}</td><td className="cell">{p.storageCondition?.description ?? "—"}</td>
          </tr>
        ))}
      </Table>
      <Pager page={page} hasNext={hasNext} href={(n) => `/catalog/${n}`} />
      <p className="mt-6 text-sm text-slate-600">Master data as of {formatDateTime(cachedAt)}. Price and stock are shown live on each product.</p>
    </>
  );
}

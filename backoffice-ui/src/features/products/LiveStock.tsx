"use client";
import { useEffect, useState } from "react";
import { formatMoney } from "@/ui/format";

type Live = { price: string; stock: number | null; active: boolean | null };

/** CSR island on an ISR page: price, stock and status are never cached, so they're read live, per user. */
export function LiveStock({ productId }: { productId: string }) {
  const [live, setLive] = useState<Live | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const abort = new AbortController();
    fetch(`/bff/products/${productId}/live`, { signal: abort.signal, cache: "no-store" })
      .then(async (r) => {
        if (r.status === 401) return window.location.assign("/login?reason=expired");
        if (!r.ok) throw new Error(((await r.json().catch(() => ({}))) as { error?: string }).error);
        setLive((await r.json()) as Live);
      })
      .catch((e: Error) => { if (!abort.signal.aborted) setError(e.message || "Live data is unavailable."); });
    return () => abort.abort();
  }, [productId]);

  if (error) return <p role="alert" className="alert-error">{error}</p>;
  if (!live) return <p role="status" className="text-slate-600">Loading live price and stock…</p>;
  return (
    <dl data-testid="live" className="grid gap-3 sm:grid-cols-3">
      <div><dt className="text-sm text-slate-600">Price</dt><dd>{formatMoney(live.price)}</dd></div>
      <div><dt className="text-sm text-slate-600">On hand</dt><dd>{live.stock ?? "—"}</dd></div>
      <div><dt className="text-sm text-slate-600">Status</dt><dd>{live.active === false ? "Inactive" : "Active"}</dd></div>
    </dl>
  );
}

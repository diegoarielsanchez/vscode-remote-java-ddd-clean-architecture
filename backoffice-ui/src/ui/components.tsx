import Link from "next/link";
import type { ReactNode } from "react";
import { humanize } from "./format";

export function PageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {children && <div className="flex flex-wrap gap-2">{children}</div>}
    </div>
  );
}

export function Card({ title, children }: { title?: string; children: ReactNode }) {
  return (
    <section className="card">
      {title && <h2 className="mb-3 text-lg font-semibold">{title}</h2>}
      {children}
    </section>
  );
}

const TONE: Record<string, string> = {
  DELIVERED: "tone-good", APPROVED: "tone-good", AVAILABLE: "tone-good", ACTIVE: "tone-good", PAID: "tone-good", OPEN: "tone-info",
  AWAITING_STOCK: "tone-info", PENDING_APPROVAL: "tone-info", ISSUED: "tone-info",
  DELIVERY_QUARANTINED: "tone-warn", QUARANTINED: "tone-warn",
  REJECTED: "tone-bad", STOCK_REJECTED: "tone-bad", DELIVERY_REJECTED: "tone-bad", CANCELLED: "tone-bad",
};
export function Badge({ code }: { code?: string | null }) {
  return <span className={`badge ${TONE[code ?? ""] ?? "tone-none"}`}>{humanize(code)}</span>;
}

export function Facts({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt className="text-sm text-slate-600">{label}</dt>
          <dd className="break-words">{value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}

export function Table({ head, children, empty }: { head: string[]; children: ReactNode[]; empty: string }) {
  if (children.length === 0) return <p className="card text-slate-600">{empty}</p>;
  return (
    <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-slate-200 bg-slate-100">
          <tr>{head.map((h) => <th key={h} scope="col" className="px-4 py-2 font-medium whitespace-nowrap">{h}</th>)}</tr>
        </thead>
        <tbody className="divide-y divide-slate-100">{children}</tbody>
      </table>
    </div>
  );
}

/** `href` builds the link of a page. No prefetch: each page costs the user a gateway call. */
export function Pager({ page, hasNext, href }: { page: number; hasNext: boolean; href: (page: number) => string }) {
  if (page === 1 && !hasNext) return null;
  return (
    <nav aria-label="Pages" className="mt-4 flex items-center gap-3">
      {page > 1 && <Link prefetch={false} className="btn-secondary" href={href(page - 1)}>Previous</Link>}
      <span className="text-sm text-slate-600">Page {page}</span>
      {hasNext && <Link prefetch={false} className="btn-secondary" href={href(page + 1)}>Next</Link>}
    </nav>
  );
}

export function Skeleton({ label = "Loading…" }: { label?: string }) {
  return <p role="status" className="card animate-pulse text-slate-500">{label}</p>;
}

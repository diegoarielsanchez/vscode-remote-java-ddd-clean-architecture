import Link from "next/link";
import type { ReactNode } from "react";
import { Pending } from "./Pending";

/** `soft`: both ends share a root layout. Otherwise a plain <a>: it's a full page load anyway (see server/csp.ts). */
const LINKS = [
  { href: "/", label: "Dashboard", group: "secure" },
  { href: "/orders", label: "Orders", group: "secure" },
  { href: "/settlements", label: "Settlements", group: "secure" },
  { href: "/hcps", label: "HCPs", group: "secure" },
  { href: "/msrs", label: "Sales reps", group: "secure" },
  { href: "/catalog/1", label: "Catalog", group: "catalog" },
  { href: "/help", label: "Help", group: "public" },
] as const;

export function Shell({ group, user, children }: { group: "secure" | "catalog" | "public"; user?: ReactNode; children: ReactNode }) {
  return (
    <>
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:p-3">Skip to content</a>
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-5 gap-y-2 px-4 py-3">
          <span className="font-semibold text-brand">Back office</span>
          <nav aria-label="Main" className="flex flex-wrap gap-x-4 gap-y-1 text-sm">
            {LINKS.map((l) => l.group === group && group !== "public"
              ? <Link key={l.href} prefetch={false} className="hover:underline" href={l.href}>{l.label}<Pending /></Link>
              : <a key={l.href} className="hover:underline" href={l.href}>{l.label}</a>)}
          </nav>
          <div className="ml-auto text-sm">{user}</div>
        </div>
      </header>
      <main id="main" className="mx-auto max-w-6xl px-4 py-8">{children}</main>
    </>
  );
}

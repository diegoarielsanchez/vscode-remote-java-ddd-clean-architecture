import type { Metadata } from "next";
import type { ReactNode } from "react";
import { headers } from "next/headers";
import "../globals.css";

export const metadata: Metadata = { title: { default: "Back office", template: "%s · Back office" }, robots: { index: false } };

/** Root layout of every dynamic (SSR) page. Reading the request makes all of them dynamic, as the CSP nonce needs. */
export default async function SecureRoot({ children }: { children: ReactNode }) {
  await headers();
  return <html lang="en"><body>{children}</body></html>;
}

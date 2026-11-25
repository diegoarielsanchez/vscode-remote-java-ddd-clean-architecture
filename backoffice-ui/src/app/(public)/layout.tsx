import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Shell } from "@/ui/Shell";
import "../globals.css";

export const metadata: Metadata = { title: { default: "Back office", template: "%s · Back office" }, robots: { index: false } };

/** Root layout of the static (SSG) pages. */
export default function PublicRoot({ children }: { children: ReactNode }) {
  return <html lang="en"><body><Shell group="public">{children}</Shell></body></html>;
}

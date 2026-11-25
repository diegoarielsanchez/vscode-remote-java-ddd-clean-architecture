import type { Metadata } from "next";
import type { ReactNode } from "react";
import { Shell } from "@/ui/Shell";
import { UserMenu } from "@/ui/UserMenu";
import "../globals.css";

export const metadata: Metadata = { title: { default: "Back office", template: "%s · Back office" }, robots: { index: false } };

/** Root layout of the ISR pages: nothing here may depend on the user. */
export default function CatalogRoot({ children }: { children: ReactNode }) {
  return <html lang="en"><body><Shell group="catalog" user={<UserMenu />}>{children}</Shell></body></html>;
}

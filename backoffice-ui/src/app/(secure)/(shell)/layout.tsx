import type { ReactNode } from "react";
import { requireSession } from "@/server/session";
import { Shell } from "@/ui/Shell";
import { UserMenu } from "@/ui/UserMenu";

/** Makes no gateway calls: it also renders for prefetches. */
export default async function ShellLayout({ children }: { children: ReactNode }) {
  const { username } = await requireSession();
  return <Shell group="secure" user={<UserMenu username={username} />}>{children}</Shell>;
}

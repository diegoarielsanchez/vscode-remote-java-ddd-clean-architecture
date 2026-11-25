"use client";
import { useEffect, useState } from "react";
import { signOut } from "@/features/auth/actions";

/** On ISR pages the user's name can't be in the (shared) HTML, so it's fetched. SSR pages pass it in. */
export function UserMenu({ username }: { username?: string }) {
  const [name, setName] = useState(username);
  useEffect(() => {
    if (username) return;
    fetch("/bff/session", { cache: "no-store" })
      .then((r) => (r.ok ? r.json() : Promise.reject(new Error())))
      .then((s: { username: string }) => setName(s.username))
      .catch(() => {});
  }, [username]);
  return (
    <form action={signOut} className="flex items-center gap-3">
      <span data-testid="user">{name ?? "…"}</span>
      <button className="btn-secondary">Sign out</button>
    </form>
  );
}

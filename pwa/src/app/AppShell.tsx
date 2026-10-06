import { NavLink, Outlet } from "react-router-dom";
import { useSession } from "../core/session";
import { authRepository } from "../features/auth/authRepository";

const TABS = [
  { to: "/visits", label: "Visits", icon: "🗂" },
  { to: "/plans", label: "Plans", icon: "📅" },
  { to: "/settlements", label: "Settlements", icon: "🧾" },
];

export function AppShell() {
  const session = useSession();
  return (
    <div className="min-h-screen pb-16">
      <header className="sticky top-0 z-20 flex h-14 items-center justify-between bg-brand px-4 text-white">
        <span className="text-lg font-semibold">MedRep</span>
        <div className="flex items-center gap-3 text-sm">
          <span className="max-w-[10rem] truncate" aria-label="Signed in as">
            {session?.username}
          </span>
          <button type="button" className="rounded-md px-2 py-1 hover:bg-white/10" onClick={authRepository.logout}>
            Sign out
          </button>
        </div>
      </header>

      <main>
        <Outlet />
      </main>

      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-20 flex h-16 border-t border-slate-200 bg-white pb-[env(safe-area-inset-bottom)]"
      >
        {TABS.map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            className={({ isActive }) =>
              `flex flex-1 flex-col items-center justify-center text-xs ${isActive ? "font-semibold text-brand" : "text-slate-500"}`
            }
          >
            <span aria-hidden className="text-lg">
              {t.icon}
            </span>
            {t.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}

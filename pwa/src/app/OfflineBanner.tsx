import { useSyncExternalStore } from "react";

const subscribe = (cb: () => void) => {
  window.addEventListener("online", cb);
  window.addEventListener("offline", cb);
  return () => {
    window.removeEventListener("online", cb);
    window.removeEventListener("offline", cb);
  };
};

/** API data is never cached offline (it's personal data), so say so instead of showing stale data. */
export function OfflineBanner() {
  const online = useSyncExternalStore(subscribe, () => navigator.onLine);
  if (online) return null;
  return (
    <div role="status" className="sticky top-0 z-30 bg-amber-500 px-4 py-1 text-center text-sm text-white">
      You're offline. Data will load when the connection is back.
    </div>
  );
}

"use client";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

/** CSR island: follows a changing order over server-sent events, then re-renders the SSR page. */
export function LiveStatus({ orderId, status }: { orderId: string; status: string }) {
  const router = useRouter();
  const [state, setState] = useState<"live" | "stopped">("live");

  useEffect(() => {
    const source = new EventSource(`/bff/orders/${orderId}/events`);
    const stop = () => { source.close(); setState("stopped"); };
    source.addEventListener("status", (e) => { if ((e as MessageEvent<string>).data !== status) router.refresh(); });
    source.addEventListener("end", stop);
    source.onerror = () => { if (source.readyState === EventSource.CLOSED) stop(); };
    return () => source.close();
  }, [orderId, status, router]);

  return (
    <p role="status" className="text-sm text-slate-600">
      {state === "live" ? "Waiting for updates…" : "Live updates stopped. Reload the page to check again."}
    </p>
  );
}

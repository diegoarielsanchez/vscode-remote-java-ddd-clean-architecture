import { useRegisterSW } from "virtual:pwa-register/react";

/**
 * registerType "prompt": a new service worker waits until the user accepts, then reloads.
 * Security fixes reach users promptly without silently swapping code under an open form.
 */
export function UpdatePrompt() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    offlineReady: [offlineReady, setOfflineReady],
    updateServiceWorker,
  } = useRegisterSW();

  if (!needRefresh && !offlineReady) return null;

  return (
    <div role="status" className="fixed inset-x-4 bottom-20 z-50 mx-auto flex max-w-md items-center gap-3 rounded-xl bg-slate-900 p-3 text-sm text-white shadow-xl">
      <p className="flex-1">{needRefresh ? "A new version is available." : "MedRep is ready to work offline."}</p>
      {needRefresh && (
        <button type="button" className="btn bg-white text-slate-900" onClick={() => void updateServiceWorker(true)}>
          Reload
        </button>
      )}
      <button
        type="button"
        className="btn text-white"
        onClick={() => {
          setNeedRefresh(false);
          setOfflineReady(false);
        }}
      >
        Dismiss
      </button>
    </div>
  );
}

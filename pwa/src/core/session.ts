import { useSyncExternalStore } from "react";
import { IDLE_TIMEOUT_MS } from "./env";
import { tokenStore } from "./http";

export type Session = { username: string; roles: string[] };
export type EndReason = "logout" | "expired" | "idle";

let current: Session | null = null;
let lastEndReason: EndReason | null = null;
let idleTimer: number | undefined;
const listeners = new Set<() => void>();
const onEndCallbacks = new Set<() => void>();
const ACTIVITY_EVENTS = ["pointerdown", "keydown", "wheel", "touchstart"] as const;

function emit() {
  listeners.forEach((listener) => listener());
}

function resetIdleTimer() {
  window.clearTimeout(idleTimer);
  idleTimer = window.setTimeout(() => sessionStore.end("idle"), IDLE_TIMEOUT_MS);
}

/**
 * Session lifecycle (OWASP A07): the token lives only in memory, the session ends on logout,
 * on a 401 from the gateway, or after IDLE_TIMEOUT_MS without interaction. Ending a session
 * runs the registered cleanups (e.g. wiping the query cache) so no personal data stays in memory.
 */
export const sessionStore = {
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },
  get: () => current,
  lastEndReason: () => lastEndReason,

  start(token: string, session: Session) {
    tokenStore.set(token);
    current = session;
    lastEndReason = null;
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, resetIdleTimer, { passive: true }));
    resetIdleTimer();
    emit();
  },

  end(reason: EndReason) {
    if (current === null && !tokenStore.has()) return;
    tokenStore.clear();
    current = null;
    lastEndReason = reason;
    window.clearTimeout(idleTimer);
    ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, resetIdleTimer));
    onEndCallbacks.forEach((cb) => cb());
    emit();
  },

  onEnd(cb: () => void) {
    onEndCallbacks.add(cb);
    return () => {
      onEndCallbacks.delete(cb);
    };
  },
};

tokenStore.onUnauthorized(() => sessionStore.end("expired"));

export function useSession(): Session | null {
  return useSyncExternalStore(sessionStore.subscribe, sessionStore.get);
}

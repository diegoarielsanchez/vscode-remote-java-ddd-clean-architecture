export type StreamEvent = { event: "status"; status: string } | { event: "end"; reason: "settled" | "timeout" | "error" };

/** Statuses another service is about to change on its own. Others wait for a person. */
export const isChanging = (status: string) => status === "AWAITING_STOCK" || status === "PENDING_APPROVAL";

/** Few calls on purpose: the gateway allows 20 per minute per client. */
export const BACKOFF_MS = [2_000, 3_000, 5_000, 8_000, 13_000, 20_000];
const MAX_MS = 5 * 60_000;

/**
 * Follows an order until it settles: yields a status whenever it changes, then one `end`.
 * The stock saga and the agent's decision are asynchronous and the backend has no push API,
 * so the BFF polls (with back-off) on behalf of the browser.
 */
export async function* followOrder(
  fetchStatus: () => Promise<string>,
  wait: (ms: number) => Promise<void>,
  now: () => number = Date.now,
): AsyncGenerator<StreamEvent> {
  const start = now();
  let last: string | null = null;
  let step = 0;
  for (;;) {
    let status: string;
    try {
      status = await fetchStatus();
    } catch {
      yield { event: "end", reason: "error" };
      return;
    }
    if (status !== last) {
      last = status;
      step = 0;
      yield { event: "status", status };
    }
    if (!isChanging(status)) {
      yield { event: "end", reason: "settled" };
      return;
    }
    if (now() - start >= MAX_MS) {
      yield { event: "end", reason: "timeout" };
      return;
    }
    await wait(BACKOFF_MS[Math.min(step++, BACKOFF_MS.length - 1)]!);
  }
}

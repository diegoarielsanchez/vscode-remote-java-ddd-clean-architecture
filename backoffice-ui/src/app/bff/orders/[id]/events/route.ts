import { z } from "zod";
import { isUuid } from "@/features/schemas";
import { callGateway } from "@/server/gateway";
import { followOrder } from "@/server/order-stream";
import { getSession, requestIp } from "@/server/session";

const MAX_STREAMS_PER_USER = 5;
const open = new Map<string, number>();

/** Server-sent events: an order's status while the stock saga and the approval run. */
export async function GET(request: Request, { params }: RouteContext<"/bff/orders/[id]/events">) {
  const session = await getSession();
  if (!session) return Response.json({ error: "Not signed in" }, { status: 401 });
  const { id } = await params;
  if (!isUuid(id)) return Response.json({ error: "Not found" }, { status: 404 });
  const { username, token } = session;
  if ((open.get(username) ?? 0) >= MAX_STREAMS_PER_USER) return Response.json({ error: "Too many live pages" }, { status: 429 });

  const clientIp = await requestIp();
  const stop = new AbortController();
  request.signal.addEventListener("abort", () => stop.abort(), { once: true });
  if (request.signal.aborted) stop.abort();

  const fetchStatus = async () =>
    (await callGateway(`api/v1/orders/${id}`, { token, clientIp, signal: stop.signal, schema: z.object({ status: z.string() }) })).status;
  const wait = (ms: number) => new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms);
    stop.signal.addEventListener("abort", () => { clearTimeout(timer); resolve(); }, { once: true });
  });

  open.set(username, (open.get(username) ?? 0) + 1);
  let released = false;
  const release = () => {
    if (released) return;
    released = true;
    stop.abort();
    const left = (open.get(username) ?? 1) - 1;
    if (left > 0) open.set(username, left); else open.delete(username);
  };
  stop.signal.addEventListener("abort", release, { once: true });

  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      try {
        for await (const e of followOrder(fetchStatus, wait)) {
          if (stop.signal.aborted) break;
          controller.enqueue(encoder.encode(`event: ${e.event}\ndata: ${e.event === "status" ? e.status : e.reason}\n\n`));
        }
        if (!stop.signal.aborted) controller.close();
      } catch {
        // The browser went away.
      } finally {
        release();
      }
    },
    cancel: release,
  });
  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream; charset=utf-8",
      "Cache-Control": "no-store, no-transform", // no-transform: compression would buffer the events
      "X-Accel-Buffering": "no",
    },
  });
}

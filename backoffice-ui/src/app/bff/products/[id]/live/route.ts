import { Live } from "@/features/products/api";
import { isUuid } from "@/features/schemas";
import { callGateway } from "@/server/gateway";
import { GatewayError } from "@/server/gateway-error";
import { getSession, requestIp } from "@/server/session";

/** Price, stock and status with the user's own JWT: the uncached half of the catalog's ISR pages. */
export async function GET(request: Request, { params }: RouteContext<"/bff/products/[id]/live">) {
  const session = await getSession();
  if (!session) return Response.json({ error: "Not signed in" }, { status: 401 });
  const { id } = await params;
  if (!isUuid(id)) return Response.json({ error: "Not found" }, { status: 404 });
  try {
    const live = await callGateway(`api/v1/products/${id}`, { token: session.token, clientIp: await requestIp(), schema: Live, signal: request.signal });
    return Response.json(live, { headers: { "Cache-Control": "no-store" } });
  } catch (e) {
    if (e instanceof GatewayError) return Response.json({ error: e.message }, { status: e.status });
    throw e;
  }
}

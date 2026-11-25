import { revalidateTag } from "next/cache";
import { z } from "zod";
import { CATALOG_TAG, SPECIALTIES_TAG } from "@/server/cacheable";
import { env } from "@/server/env";
import { verify } from "@/server/signature";
import { nowSeconds } from "@/server/token";

const Body = z.discriminatedUnion("type", [
  z.object({ type: z.literal("product") }),
  z.object({ type: z.literal("specialties") }),
]);

/**
 * On-demand ISR: called when master data changes outside this app (e.g. by a consumer of
 * catalog.events). Signed with REVALIDATE_SECRET; see README.
 */
export async function POST(request: Request) {
  const secret = env.revalidateSecret;
  if (!secret) return new Response(null, { status: 404 });
  const body = (await request.text()).slice(0, 2_000);
  if (!verify(secret, request.headers.get("x-timestamp"), request.headers.get("x-signature"), body, nowSeconds())) {
    return Response.json({ error: "Invalid signature" }, { status: 401 });
  }
  let json: unknown;
  try { json = JSON.parse(body); } catch { json = null; }
  const input = Body.safeParse(json);
  if (!input.success) return Response.json({ error: "Invalid body" }, { status: 400 });
  // expire: 0 = don't serve the old one again.
  revalidateTag(input.data.type === "product" ? CATALOG_TAG : SPECIALTIES_TAG, { expire: 0 });
  return Response.json({ revalidated: true });
}

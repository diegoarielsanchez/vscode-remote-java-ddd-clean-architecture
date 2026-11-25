import { endSession } from "@/server/session";

/** The gateway refused the session's JWT. Pages can't change cookies while rendering, so they redirect here. */
export async function GET() {
  await endSession();
  return new Response(null, { status: 303, headers: { Location: "/login?reason=expired" } });
}

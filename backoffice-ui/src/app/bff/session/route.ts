import { getSession } from "@/server/session";

/** Who is signed in, for the header of ISR pages. Never includes the token. */
export async function GET() {
  const session = await getSession();
  return session
    ? Response.json({ username: session.username, roles: session.roles }, { headers: { "Cache-Control": "no-store" } })
    : Response.json({ error: "Not signed in" }, { status: 401 });
}

import { redirect } from "next/navigation";
import { signIn } from "@/features/auth/actions";
import { getSession } from "@/server/session";
import { safeNext } from "@/server/routes";
import { ActionForm, Field } from "@/ui/form";

export const metadata = { title: "Sign in" };

/** SSR, not SSG: people type passwords here, so it gets the strict nonce CSP. */
export default async function LoginPage({ searchParams }: PageProps<"/login">) {
  const { next, reason } = await searchParams;
  const target = safeNext(typeof next === "string" ? next : null);
  if (await getSession()) redirect(target);
  return (
    <main className="mx-auto mt-16 max-w-sm px-4">
      <div className="card grid gap-4">
        <h1 className="text-xl font-semibold">Back office</h1>
        {reason === "expired" && <p role="status" className="alert-error">Your session has expired. Please sign in again.</p>}
        <ActionForm action={signIn} submit="Sign in">
          <input type="hidden" name="next" value={target} />
          <Field name="username" label="Username" autoComplete="username" required maxLength={64} />
          <Field name="password" label="Password" type="password" autoComplete="current-password" required maxLength={128} />
        </ActionForm>
        <a className="link text-sm" href="/help">Help</a>
      </div>
    </main>
  );
}

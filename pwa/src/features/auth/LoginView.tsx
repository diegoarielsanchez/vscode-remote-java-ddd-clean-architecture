import { Field } from "../../core/ui/components";
import { useLoginViewModel } from "./useLoginViewModel";

export function LoginView() {
  const vm = useLoginViewModel();
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <form onSubmit={vm.submit} className="card w-full max-w-sm space-y-4 p-6" noValidate>
        <div>
          <h1 className="text-2xl font-semibold text-brand">MedRep</h1>
          <p className="text-sm text-slate-500">Sign in with your platform account</p>
        </div>
        {vm.notice && (
          <p role="status" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
            {vm.notice}
          </p>
        )}
        <Field label="Username">
          {(props) => (
            <input
              {...props}
              name="username"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={64}
              required
              value={vm.username}
              onChange={(e) => vm.setUsername(e.target.value)}
            />
          )}
        </Field>
        <Field label="Password">
          {(props) => (
            <input
              {...props}
              name="password"
              type="password"
              autoComplete="current-password"
              maxLength={128}
              required
              value={vm.password}
              onChange={(e) => vm.setPassword(e.target.value)}
            />
          )}
        </Field>
        {vm.error && (
          <p role="alert" className="field-error">
            {vm.error}
          </p>
        )}
        <button type="submit" className="btn-primary w-full" disabled={!vm.canSubmit}>
          {vm.submitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </main>
  );
}

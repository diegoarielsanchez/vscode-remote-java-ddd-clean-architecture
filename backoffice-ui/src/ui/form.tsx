"use client";
import { createContext, startTransition, useActionState, useContext, useEffect, useId, useRef, type ReactNode } from "react";

type State = { error?: string; fields?: Record<string, string>; ok?: string };
type Action = (state: State, form: FormData) => Promise<State>;

const Fields = createContext<Record<string, string>>({});

/**
 * A form bound to a Server Action. Without (or before) hydration the browser posts it normally.
 * Hydrated, it submits through a transition, so a rejected form keeps what the person typed
 * (React would reset it); password fields are always cleared.
 */
export function ActionForm({ action, children, submit, tone = "primary", confirm, className = "grid gap-4" }: {
  action: Action; children?: ReactNode; submit: string; tone?: "primary" | "secondary" | "danger"; confirm?: string; className?: string;
}) {
  const [state, run, pending] = useActionState(action, {});
  const form = useRef<HTMLFormElement>(null);
  useEffect(() => {
    if (state.ok) form.current?.reset();
    else form.current?.querySelectorAll<HTMLInputElement>("input[type=password]").forEach((i) => { i.value = ""; });
  }, [state]);
  return (
    <form
      ref={form}
      action={run}
      className={className}
      onSubmit={(e) => {
        e.preventDefault();
        if (confirm && !window.confirm(confirm)) return;
        const data = new FormData(e.currentTarget);
        startTransition(() => run(data));
      }}
    >
      {state.error && <p role="alert" className="alert-error">{state.error}</p>}
      {state.ok && <p role="status" className="alert-ok">{state.ok}</p>}
      <Fields value={state.fields ?? {}}>{children}</Fields>
      <div><button type="submit" disabled={pending} className={`btn-${tone}`}>{pending ? "Working…" : submit}</button></div>
    </form>
  );
}

function Labelled({ id, name, label, hint, children }: { id: string; name: string; label: string; hint?: string; children: ReactNode }) {
  const error = useContext(Fields)[name];
  return (
    <div>
      <label className="label" htmlFor={id}>{label}</label>
      {children}
      {hint && <p className="mt-1 text-sm text-slate-600">{hint}</p>}
      {error && <p id={`${id}-error`} className="field-error">{error}</p>}
    </div>
  );
}

function useField(name: string) {
  const id = useId(); // several forms on a page may have a "reason" field
  const error = useContext(Fields)[name];
  return { id, name, className: "field", ...(error ? { "aria-invalid": true, "aria-describedby": `${id}-error` } : {}) };
}

export function Field({ name, label, hint, ...input }: { name: string; label: string; hint?: string } & React.ComponentProps<"input">) {
  const field = useField(name);
  return (
    <Labelled id={field.id} name={name} label={label} hint={hint}>
      <input {...input} {...field} />
    </Labelled>
  );
}

export function TextArea({ name, label, hint, ...input }: { name: string; label: string; hint?: string } & React.ComponentProps<"textarea">) {
  const field = useField(name);
  return (
    <Labelled id={field.id} name={name} label={label} hint={hint}>
      <textarea rows={2} {...input} {...field} />
    </Labelled>
  );
}

export function Select({ name, label, hint, options, ...input }: {
  name: string; label: string; hint?: string; options: { value: string; label: string }[];
} & React.ComponentProps<"select">) {
  const field = useField(name);
  return (
    <Labelled id={field.id} name={name} label={label} hint={hint}>
      <select {...input} {...field}>
        {options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </select>
    </Labelled>
  );
}

export function Check({ name, label, value, defaultChecked }: { name: string; label: string; value?: string; defaultChecked?: boolean }) {
  return (
    <label className="flex items-center gap-2">
      <input type="checkbox" name={name} value={value ?? "on"} defaultChecked={defaultChecked} /> {label}
    </label>
  );
}

import type { FormEvent, ReactNode } from "react";
import { ErrorBox, PageHeader, Spinner } from "./components";

/** Header with back link + save button, load/error states, and a submit-on-enter form body. */
export function FormPage(p: {
  title: string;
  back: string;
  isLoading: boolean;
  loadError: string | null;
  onRetryLoad: () => void;
  saving: boolean;
  error: string | null;
  onSubmit: () => void;
  children: ReactNode;
}) {
  const formId = "entity-form";
  const submit = (e: FormEvent) => {
    e.preventDefault();
    p.onSubmit();
  };
  return (
    <>
      <PageHeader
        title={p.title}
        back={p.back}
        actions={
          <button type="submit" form={formId} className="btn-primary" disabled={p.saving || p.isLoading || !!p.loadError}>
            {p.saving ? "Saving…" : "Save"}
          </button>
        }
      />
      {p.isLoading ? (
        <Spinner />
      ) : p.loadError ? (
        <ErrorBox message={p.loadError} onRetry={p.onRetryLoad} />
      ) : (
        <form id={formId} onSubmit={submit} noValidate className="mx-auto max-w-xl space-y-4 p-4 pb-24">
          {p.children}
          {p.error && (
            <p role="alert" className="field-error">
              {p.error}
            </p>
          )}
        </form>
      )}
    </>
  );
}

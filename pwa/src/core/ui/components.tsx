import { useEffect, useId, useRef, type ReactNode } from "react";
import { Link } from "react-router-dom";

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center p-8 text-slate-500">
      <span className="mr-3 h-5 w-5 animate-spin rounded-full border-2 border-brand border-t-transparent" aria-hidden />
      {label}
    </div>
  );
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="m-4 rounded-lg border border-red-200 bg-red-50 p-4 text-red-800">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="btn-secondary mt-3" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="p-8 text-center text-slate-500">{children}</p>;
}

/** Polite live region for success/failure messages ("Invoice added"). */
export function Flash({ message, onDismiss }: { message: string | null; onDismiss: () => void }) {
  useEffect(() => {
    if (!message) return;
    const t = window.setTimeout(onDismiss, 5000);
    return () => window.clearTimeout(t);
  }, [message, onDismiss]);
  return (
    <div role="status" aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-20 z-40 flex justify-center px-4">
      {message && (
        <p className="pointer-events-auto rounded-lg bg-slate-900 px-4 py-2 text-sm text-white shadow-lg">{message}</p>
      )}
    </div>
  );
}

export function PageHeader({ title, back, actions }: { title: string; back?: string; actions?: ReactNode }) {
  return (
    <div className="sticky top-14 z-10 flex items-center gap-2 border-b border-slate-200 bg-white/95 px-4 py-3 backdrop-blur">
      {back && (
        <Link to={back} className="btn-ghost -ml-2 px-2" aria-label="Back">
          ←
        </Link>
      )}
      <h1 className="flex-1 truncate text-lg font-semibold">{title}</h1>
      {actions}
    </div>
  );
}

type FieldProps = {
  label: string;
  error?: string;
  hint?: string;
  children: (props: { id: string; "aria-invalid": boolean; "aria-describedby"?: string; className: string }) => ReactNode;
};

/** Label + control + error wired together for screen readers. */
export function Field({ label, error, hint, children }: FieldProps) {
  const id = useId();
  const describedBy = [error && `${id}-error`, hint && `${id}-hint`].filter(Boolean).join(" ") || undefined;
  return (
    <div>
      <label htmlFor={id} className="label">
        {label}
      </label>
      {children({ id, "aria-invalid": !!error, "aria-describedby": describedBy, className: `field ${error ? "field-invalid" : ""}` })}
      {hint && (
        <p id={`${id}-hint`} className="mt-1 text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="field-error">
          {error}
        </p>
      )}
    </div>
  );
}

export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  onConfirm,
  onCancel,
}: {
  title: string;
  message: string;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  useEffect(() => cancelRef.current?.focus(), []);
  return (
    <Modal title={title} onClose={onCancel}>
      <p className="text-slate-700">{message}</p>
      <div className="mt-6 flex justify-end gap-2">
        <button ref={cancelRef} type="button" className="btn-secondary" onClick={onCancel}>
          Cancel
        </button>
        <button type="button" className="btn-danger" onClick={onConfirm}>
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}

/** Bottom sheet on phones, centered dialog on larger screens. Escape and backdrop close it. */
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const titleId = useId();
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="max-h-[90vh] w-full overflow-y-auto rounded-t-2xl bg-white p-4 pb-8 shadow-xl sm:max-w-lg sm:rounded-2xl sm:pb-4"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={(e) => {
          if (e.key === "Escape") {
            e.stopPropagation();
            onClose();
          }
        }}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 id={titleId} className="text-lg font-semibold">
            {title}
          </h2>
          <button type="button" className="btn-ghost px-2" aria-label="Close" onClick={onClose}>
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="px-4 py-3">
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 whitespace-pre-wrap break-words">{children}</dd>
    </div>
  );
}

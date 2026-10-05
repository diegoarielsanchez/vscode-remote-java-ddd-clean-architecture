import { useEffect, useRef } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ACCEPT_ATTRIBUTE } from "../../core/files";
import { seg } from "../../core/http";
import { Detail, ErrorBox, Field, Flash, PageHeader, Spinner } from "../../core/ui/components";
import { FormPage } from "../../core/ui/FormPage";
import { PagedList } from "../../core/ui/PagedList";
import { HcpPicker } from "../hcp/HcpPicker";
import { MsrPicker } from "../msr/MsrPicker";
import {
  useVisitDetailViewModel,
  useVisitFormViewModel,
  useVisitListViewModel,
  useVisitPlanFormViewModel,
  useVisitPlanListViewModel,
} from "./visitViewModels";

const NewButton = ({ to, label }: { to: string; label: string }) => (
  <Link to={to} className="btn-primary">
    + {label}
  </Link>
);

// ── Visits ───────────────────────────────────────────────────────────────────────

export function VisitListView() {
  const vm = useVisitListViewModel();
  return (
    <>
      <PageHeader title="Visits" actions={<NewButton to="/visits/new" label="New" />} />
      <PagedList
        isLoading={vm.isLoading}
        error={vm.error}
        isEmpty={vm.rows.length === 0}
        emptyText="No visits yet."
        hasMore={vm.hasMore}
        isFetchingMore={vm.isFetchingMore}
        onLoadMore={vm.loadMore}
        onRetry={vm.retry}
      >
        {vm.rows.map((r) => (
          <li key={r.id}>
            <Link to={`/visits/${seg(r.id)}`} className="flex items-center gap-3 px-4 py-3 hover:bg-slate-50">
              <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-500">{r.date}</p>
                <p className="truncate font-medium">{r.hcp}</p>
                {r.details && <p className="truncate text-sm text-slate-600">{r.details}</p>}
              </div>
              {r.files > 0 && <span className="text-xs text-slate-500">{r.files} files</span>}
            </Link>
          </li>
        ))}
      </PagedList>
    </>
  );
}

export function VisitDetailView() {
  const { id = "" } = useParams();
  const vm = useVisitDetailViewModel(id);
  const fileInput = useRef<HTMLInputElement>(null);

  return (
    <>
      <PageHeader
        title="Visit"
        back="/visits"
        actions={
          vm.visit && (
            <Link to={`/visits/${seg(id)}/edit`} className="btn-secondary">
              Edit
            </Link>
          )
        }
      />
      {vm.isLoading ? (
        <Spinner />
      ) : vm.error ? (
        <ErrorBox message={vm.error} onRetry={vm.retry} />
      ) : (
        vm.visit && (
          <div className="mx-auto max-w-xl pb-24">
            <dl className="card m-4 divide-y divide-slate-100">
              <Detail label="Date">{vm.visit.date}</Detail>
              <Detail label="Healthcare professional">{vm.visit.hcp}</Detail>
              <Detail label="Medical sales rep">{vm.visit.msr}</Detail>
              <Detail label="Site">{vm.visit.site}</Detail>
              <Detail label="Comments">{vm.visit.comments}</Detail>
            </dl>

            <section className="card m-4 p-4" aria-labelledby="attachments-title">
              <div className="flex items-center justify-between">
                <h2 id="attachments-title" className="font-semibold">
                  Product promo attachments
                </h2>
                <button
                  type="button"
                  className="btn-secondary"
                  disabled={vm.uploading}
                  onClick={() => fileInput.current?.click()}
                >
                  {vm.uploading ? "Uploading…" : "Attach files"}
                </button>
                <input
                  ref={fileInput}
                  type="file"
                  multiple
                  accept={ACCEPT_ATTRIBUTE}
                  className="hidden"
                  aria-label="Attachment files"
                  onChange={(e) => {
                    vm.upload(Array.from(e.target.files ?? []));
                    e.target.value = "";
                  }}
                />
              </div>
              {vm.visit.attachments.length === 0 ? (
                <p className="mt-2 text-sm text-slate-500">None yet. Allowed: .pdf .xlsx .docx .txt</p>
              ) : (
                <ul className="mt-2 divide-y divide-slate-100">
                  {vm.visit.attachments.map((a) => (
                    <li key={`${a.fileName}-${a.sha256Hash ?? ""}`} className="py-2">
                      <p className="break-all">{a.fileName}</p>
                      {a.sha256Hash && <p className="text-xs text-slate-500">SHA-256 {a.sha256Hash.slice(0, 16)}…</p>}
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )
      )}
      <Flash message={vm.flash} onDismiss={vm.clearFlash} />
    </>
  );
}

export function VisitFormView() {
  const { id } = useParams();
  const vm = useVisitFormViewModel(id);
  const navigate = useNavigate();

  useEffect(() => {
    if (vm.savedId) navigate(`/visits/${seg(vm.savedId)}`, { replace: true });
  }, [vm.savedId, navigate]);

  return (
    <FormPage
      title={vm.isEdit ? "Edit visit" : "New visit"}
      back={id ? `/visits/${seg(id)}` : "/visits"}
      isLoading={vm.isLoading}
      loadError={vm.loadError}
      onRetryLoad={vm.retryLoad}
      saving={vm.saving}
      error={vm.error}
      onSubmit={vm.submit}
    >
      <Field label="Visit date" error={vm.errors.visitDate}>
        {(props) => (
          <input {...props} type="date" required value={vm.form.visitDate} onChange={(e) => vm.setField("visitDate", e.target.value)} />
        )}
      </Field>
      <HcpPicker
        value={vm.form.healthCareProfId}
        onChange={(v) => vm.setField("healthCareProfId", v)}
        error={vm.errors.healthCareProfId}
      />
      <MsrPicker
        value={vm.form.medicalSalesRepId}
        onChange={(v) => vm.setField("medicalSalesRepId", v)}
        error={vm.errors.medicalSalesRepId}
      />
      <Field label="Visit site ID" error={vm.errors.visitSiteId}>
        {(props) => (
          <input {...props} maxLength={100} required value={vm.form.visitSiteId} onChange={(e) => vm.setField("visitSiteId", e.target.value)} />
        )}
      </Field>
      <Field label="Comments" error={vm.errors.visitComments}>
        {(props) => (
          <textarea {...props} rows={4} maxLength={2000} value={vm.form.visitComments} onChange={(e) => vm.setField("visitComments", e.target.value)} />
        )}
      </Field>
    </FormPage>
  );
}

// ── Visit plans ──────────────────────────────────────────────────────────────────

export function VisitPlanListView() {
  const vm = useVisitPlanListViewModel();
  return (
    <>
      <PageHeader title="Visit plans" actions={<NewButton to="/plans/new" label="New" />} />
      <PagedList
        isLoading={vm.isLoading}
        error={vm.error}
        isEmpty={vm.rows.length === 0}
        emptyText="No visit plans yet."
        hasMore={vm.hasMore}
        isFetchingMore={vm.isFetchingMore}
        onLoadMore={vm.loadMore}
        onRetry={vm.retry}
      >
        {vm.rows.map((r) => (
          <li key={r.id}>
            <Link to={`/plans/${seg(r.id)}`} className="flex items-center gap-3 px-4 py-3 hover:bg-slate-50">
              <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-500">{r.when}</p>
                <p className="truncate font-medium">{r.hcp}</p>
                {r.details && <p className="truncate text-sm text-slate-600">{r.details}</p>}
              </div>
              <span
                className={`rounded-full px-2 py-0.5 text-xs ${r.active ? "bg-green-100 text-green-800" : "bg-slate-200 text-slate-600"}`}
              >
                {r.active ? "Active" : "Inactive"}
              </span>
            </Link>
          </li>
        ))}
      </PagedList>
    </>
  );
}

export function VisitPlanFormView() {
  const { id } = useParams();
  const vm = useVisitPlanFormViewModel(id);
  const navigate = useNavigate();

  useEffect(() => {
    if (vm.saved) navigate("/plans", { replace: true });
  }, [vm.saved, navigate]);

  return (
    <FormPage
      title={vm.isEdit ? "Edit visit plan" : "New visit plan"}
      back="/plans"
      isLoading={vm.isLoading}
      loadError={vm.loadError}
      onRetryLoad={vm.retryLoad}
      saving={vm.saving}
      error={vm.error}
      onSubmit={vm.submit}
    >
      {vm.isEdit && !vm.active && <p className="rounded-lg bg-slate-100 p-3 text-sm">This plan is inactive.</p>}
      <Field label="Date and time" error={vm.errors.visitDateTime} hint="Must be in the future">
        {(props) => (
          <input
            {...props}
            type="datetime-local"
            required
            value={vm.form.visitDateTime}
            onChange={(e) => vm.setField("visitDateTime", e.target.value)}
          />
        )}
      </Field>
      <HcpPicker
        value={vm.form.healthCareProfId}
        onChange={(v) => vm.setField("healthCareProfId", v)}
        error={vm.errors.healthCareProfId}
      />
      <MsrPicker
        value={vm.form.medicalSalesRepId}
        onChange={(v) => vm.setField("medicalSalesRepId", v)}
        error={vm.errors.medicalSalesRepId}
      />
      <Field label="Visit site ID" error={vm.errors.visitSiteId}>
        {(props) => (
          <input {...props} maxLength={100} required value={vm.form.visitSiteId} onChange={(e) => vm.setField("visitSiteId", e.target.value)} />
        )}
      </Field>
      <Field label="Comments" error={vm.errors.visitComments}>
        {(props) => (
          <textarea {...props} rows={4} maxLength={2000} value={vm.form.visitComments} onChange={(e) => vm.setField("visitComments", e.target.value)} />
        )}
      </Field>
    </FormPage>
  );
}

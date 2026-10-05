import { useEffect } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ACCEPT_ATTRIBUTE } from "../../core/files";
import { seg } from "../../core/http";
import { ConfirmDialog, ErrorBox, Field, Flash, Modal, PageHeader, Spinner } from "../../core/ui/components";
import { FormPage } from "../../core/ui/FormPage";
import { PagedList } from "../../core/ui/PagedList";
import { MsrPicker } from "../msr/MsrPicker";
import { useSettlementDetailViewModel, useSettlementFormViewModel, useSettlementListViewModel } from "./settlementViewModels";

export function SettlementListView() {
  const vm = useSettlementListViewModel();
  return (
    <>
      <PageHeader title="Settlements" actions={<Link to="/settlements/new" className="btn-primary">+ New</Link>} />
      <PagedList
        isLoading={vm.isLoading}
        error={vm.error}
        isEmpty={vm.rows.length === 0}
        emptyText="No settlements yet."
        hasMore={vm.hasMore}
        isFetchingMore={vm.isFetchingMore}
        onLoadMore={vm.loadMore}
        onRetry={vm.retry}
      >
        {vm.rows.map((r) => (
          <li key={r.id}>
            <Link to={`/settlements/${seg(r.id)}`} className="flex items-center gap-3 px-4 py-3 hover:bg-slate-50">
              <div className="min-w-0 flex-1">
                <p className="text-xs text-slate-500">{r.meta}</p>
                <p className="truncate font-medium">{r.description}</p>
                <p className="truncate text-sm text-slate-600">{r.details}</p>
              </div>
              <span className="font-semibold tabular-nums">{r.total}</span>
            </Link>
          </li>
        ))}
      </PagedList>
    </>
  );
}

export function SettlementDetailView() {
  const { id = "" } = useParams();
  const vm = useSettlementDetailViewModel(id);
  const s = vm.settlement;

  return (
    <>
      <PageHeader
        title="Settlement"
        back="/settlements"
        actions={
          s?.canEdit && (
            <Link to={`/settlements/${seg(id)}/edit`} className="btn-secondary">
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
        s && (
          <div className="mx-auto max-w-xl pb-24">
            <section className="card m-4 p-4">
              <p className="text-xs text-slate-500">{s.meta}</p>
              <div className="flex items-start justify-between gap-3">
                <h2 className="text-xl font-semibold break-words">{s.description}</h2>
                <p className="text-xl font-semibold tabular-nums">{s.total}</p>
              </div>
              <p className="text-sm text-slate-600">{s.msr}</p>
              {!s.canEdit && <p className="mt-2 text-xs text-slate-500">Remove all invoices to edit the settlement details.</p>}
            </section>

            <section className="card m-4" aria-labelledby="invoices-title">
              <div className="flex items-center justify-between p-4">
                <h2 id="invoices-title" className="font-semibold">
                  Invoices
                </h2>
                <button type="button" className="btn-primary" onClick={vm.openInvoice}>
                  + Add invoice
                </button>
              </div>
              {s.invoices.length === 0 ? (
                <p className="px-4 pb-4 text-sm text-slate-500">No invoices yet.</p>
              ) : (
                <ul className="divide-y divide-slate-100 border-t border-slate-100">
                  {s.invoices.map((inv) => (
                    <li key={inv.id} className="flex items-center gap-3 px-4 py-3">
                      <div className="min-w-0 flex-1">
                        <p className="text-xs text-slate-500">{inv.meta}</p>
                        <p className="font-medium">{inv.title}</p>
                        {inv.file && <p className="truncate text-sm text-slate-600">{inv.file}</p>}
                      </div>
                      <button
                        type="button"
                        className="btn-ghost px-2 text-red-600"
                        disabled={vm.removing}
                        aria-label={`Remove invoice ${inv.title}`}
                        onClick={() => vm.askRemove(inv.id)}
                      >
                        Remove
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )
      )}

      {vm.invoiceOpen && <AddInvoiceDialog vm={vm} />}
      {vm.pendingRemoval && (
        <ConfirmDialog
          title="Remove invoice?"
          message="The invoice and its uploaded file will be deleted."
          confirmLabel="Remove"
          onConfirm={vm.confirmRemove}
          onCancel={vm.cancelRemove}
        />
      )}
      <Flash message={vm.flash} onDismiss={vm.clearFlash} />
    </>
  );
}

function AddInvoiceDialog({ vm }: { vm: ReturnType<typeof useSettlementDetailViewModel> }) {
  const f = vm.invoiceForm;
  const e = vm.invoiceErrors;
  return (
    <Modal title="Add invoice" onClose={vm.closeInvoice}>
      <form
        noValidate
        className="space-y-4"
        onSubmit={(ev) => {
          ev.preventDefault();
          vm.submitInvoice();
        }}
      >
        <Field label="Invoice number" error={e.invoiceNumber}>
          {(p) => <input {...p} maxLength={50} required value={f.invoiceNumber} onChange={(ev) => vm.setInvoiceField("invoiceNumber", ev.target.value)} />}
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Issue date" error={e.issueDate}>
            {(p) => <input {...p} type="date" required value={f.issueDate} onChange={(ev) => vm.setInvoiceField("issueDate", ev.target.value)} />}
          </Field>
          <Field label="Due date (optional)" error={e.dueDate}>
            {(p) => <input {...p} type="date" value={f.dueDate} onChange={(ev) => vm.setInvoiceField("dueDate", ev.target.value)} />}
          </Field>
        </div>
        <Field label="Amount" error={e.amount}>
          {(p) => (
            <input
              {...p}
              inputMode="decimal"
              autoComplete="off"
              placeholder="0.00"
              required
              value={f.amount}
              onChange={(ev) => vm.setInvoiceField("amount", ev.target.value.replace(",", "."))}
            />
          )}
        </Field>
        <Field label="Invoice file" error={e.file} hint=".pdf .xlsx .docx .txt — max 10 MB">
          {(p) => (
            <input
              {...p}
              type="file"
              accept={ACCEPT_ATTRIBUTE}
              required
              onChange={(ev) => vm.setInvoiceField("file", ev.target.files?.[0] ?? null)}
            />
          )}
        </Field>
        {vm.invoiceError && (
          <p role="alert" className="field-error">
            {vm.invoiceError}
          </p>
        )}
        <button type="submit" className="btn-primary w-full" disabled={vm.invoiceSaving}>
          {vm.invoiceSaving ? "Uploading…" : "Add invoice"}
        </button>
      </form>
    </Modal>
  );
}

/** Settlement header. Invoices are added on the detail screen (each needs its file uploaded). */
export function SettlementFormView() {
  const { id } = useParams();
  const vm = useSettlementFormViewModel(id);
  const navigate = useNavigate();

  useEffect(() => {
    if (vm.savedId) navigate(`/settlements/${seg(vm.savedId)}`, { replace: true });
  }, [vm.savedId, navigate]);

  return (
    <FormPage
      title={vm.isEdit ? "Edit settlement" : "New settlement"}
      back={id ? `/settlements/${seg(id)}` : "/settlements"}
      isLoading={vm.isLoading}
      loadError={vm.loadError}
      onRetryLoad={vm.retryLoad}
      saving={vm.saving}
      error={vm.error}
      onSubmit={vm.submit}
    >
      <Field label="Description" error={vm.errors.description}>
        {(p) => <input {...p} maxLength={255} required value={vm.form.description} onChange={(e) => vm.setField("description", e.target.value)} />}
      </Field>
      <Field label="Settlement date" error={vm.errors.settlementDate}>
        {(p) => <input {...p} type="date" required value={vm.form.settlementDate} onChange={(e) => vm.setField("settlementDate", e.target.value)} />}
      </Field>
      <MsrPicker
        value={vm.form.medicalSalesRepId}
        onChange={(v) => vm.setField("medicalSalesRepId", v)}
        error={vm.errors.medicalSalesRepId}
      />
      {!vm.isEdit && <p className="text-sm text-slate-500">After saving you'll add invoices with their files.</p>}
    </FormPage>
  );
}

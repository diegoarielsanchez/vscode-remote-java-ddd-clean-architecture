import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { validateUpload } from "../../core/files";
import { AMOUNT_PATTERN, formatDate, formatMoney, todayIso } from "../../core/format";
import { ApiError, type FieldErrors } from "../../core/http";
import { useFormState } from "../../core/useFormState";
import { useMsrDirectory } from "../msr/useMsrDirectory";
import { canEditHeader, settlementRepository } from "./settlementRepository";

const PAGE_SIZE = 20;

const fieldAwareError = (e: Error | null) => (!e || (e instanceof ApiError && e.fields) ? null : e.message);

// ── List ─────────────────────────────────────────────────────────────────────────

export function useSettlementListViewModel() {
  const q = useInfiniteQuery({
    queryKey: ["settlements"],
    queryFn: ({ pageParam, signal }) => settlementRepository.list(pageParam, PAGE_SIZE, signal),
    initialPageParam: 1,
    getNextPageParam: (last, all) => (last.length < PAGE_SIZE ? undefined : all.length + 1),
  });
  const msr = useMsrDirectory();

  const rows = (q.data?.pages.flat() ?? []).map((s) => ({
    id: s.id,
    description: s.description,
    meta: [formatDate(s.settlementDate), s.status].filter(Boolean).join(" · "),
    details: [msr.nameOf(s.medicalSalesRepId), `${s.invoices.length} invoice(s)`].filter(Boolean).join(" · "),
    total: formatMoney(s.totalAmount),
  }));

  return {
    rows,
    isLoading: q.isPending,
    error: q.error?.message ?? null,
    hasMore: q.hasNextPage,
    isFetchingMore: q.isFetchingNextPage,
    loadMore: () => void q.fetchNextPage(),
    retry: () => void q.refetch(),
  };
}

// ── Detail (+ add / remove invoice) ──────────────────────────────────────────────

type InvoiceForm = { invoiceNumber: string; issueDate: string; dueDate: string; amount: string; file: File | null };
const emptyInvoice = (): InvoiceForm => ({ invoiceNumber: "", issueDate: todayIso(), dueDate: "", amount: "", file: null });

export function useSettlementDetailViewModel(id: string) {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["settlement", id], queryFn: ({ signal }) => settlementRepository.get(id, signal) });
  const msr = useMsrDirectory();
  const [flash, setFlash] = useState<string | null>(null);
  const [invoiceOpen, setInvoiceOpen] = useState(false);
  const [pendingRemoval, setPendingRemoval] = useState<string | null>(null);
  const invoice = useFormState<InvoiceForm>(emptyInvoice());

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ["settlement", id] });
    void qc.invalidateQueries({ queryKey: ["settlements"] });
  };

  const add = useMutation({
    mutationFn: (f: InvoiceForm) =>
      settlementRepository.addInvoice(id, {
        invoiceNumber: f.invoiceNumber.trim(),
        issueDate: f.issueDate,
        dueDate: f.dueDate || undefined,
        amount: f.amount,
        file: f.file!,
      }),
    onSuccess: () => {
      setInvoiceOpen(false);
      setFlash("Invoice added.");
      refresh();
    },
    onError: (e) => e instanceof ApiError && e.fields && invoice.setErrors(e.fields),
  });

  const remove = useMutation({
    mutationFn: (invoiceId: string) => settlementRepository.removeInvoice(id, invoiceId),
    onSuccess: (s) => {
      qc.setQueryData(["settlement", id], s);
      setFlash("Invoice removed.");
      void qc.invalidateQueries({ queryKey: ["settlements"] });
    },
    onError: (e) => setFlash(e.message),
  });

  const s = q.data;
  return {
    isLoading: q.isPending,
    error: q.error?.message ?? null,
    retry: () => void q.refetch(),
    settlement: s && {
      id: s.id,
      description: s.description,
      meta: [formatDate(s.settlementDate), s.status].filter(Boolean).join(" · "),
      msr: msr.nameOf(s.medicalSalesRepId) ?? s.medicalSalesRepId,
      total: formatMoney(s.totalAmount),
      canEdit: canEditHeader(s),
      invoices: s.invoices.map((i) => ({
        id: i.id,
        title: `#${i.invoiceNumber} — ${formatMoney(i.amount)}`,
        meta: [`Issued ${formatDate(i.issueDate)}`, i.dueDate && `due ${formatDate(i.dueDate)}`, i.status].filter(Boolean).join(" · "),
        file: i.fileName ? [i.fileName, i.sizeInBytes != null && `${Math.ceil(i.sizeInBytes / 1024)} KB`].filter(Boolean).join(" · ") : null,
      })),
    },
    flash,
    clearFlash: () => setFlash(null),

    // add invoice
    invoiceOpen,
    invoiceForm: invoice.form,
    invoiceErrors: invoice.errors,
    invoiceSaving: add.isPending,
    invoiceError: fieldAwareError(add.error),
    setInvoiceField: invoice.setField,
    openInvoice() {
      invoice.setForm(emptyInvoice());
      invoice.setErrors({});
      add.reset();
      setInvoiceOpen(true);
    },
    closeInvoice: () => !add.isPending && setInvoiceOpen(false),
    submitInvoice() {
      const f = invoice.form;
      const e: FieldErrors = {};
      if (!f.invoiceNumber.trim()) e.invoiceNumber = "Required";
      if (!f.issueDate) e.issueDate = "Required";
      if (f.dueDate && f.dueDate < f.issueDate) e.dueDate = "Must be on or after the issue date";
      if (!AMOUNT_PATTERN.test(f.amount)) e.amount = "Enter an amount like 1250.50";
      const fileProblem = f.file ? validateUpload(f.file) : "Attach the invoice file";
      if (fileProblem) e.file = fileProblem;
      invoice.setErrors(e);
      if (Object.keys(e).length === 0 && !add.isPending) add.mutate(f);
    },

    // remove invoice
    pendingRemoval,
    askRemove: (invoiceId: string) => setPendingRemoval(invoiceId),
    cancelRemove: () => setPendingRemoval(null),
    confirmRemove() {
      if (pendingRemoval) remove.mutate(pendingRemoval);
      setPendingRemoval(null);
    },
    removing: remove.isPending,
  };
}

// ── Form (header only) ───────────────────────────────────────────────────────────

type SettlementForm = { description: string; settlementDate: string; medicalSalesRepId: string | null };

export function useSettlementFormViewModel(id?: string) {
  const qc = useQueryClient();
  const existing = useQuery({
    queryKey: ["settlement", id],
    queryFn: ({ signal }) => settlementRepository.get(id!, signal),
    enabled: !!id,
  });
  const { form, setForm, errors, setErrors, setField } = useFormState<SettlementForm>({
    description: "",
    settlementDate: todayIso(),
    medicalSalesRepId: null,
  });
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (!loaded && existing.data) {
      setForm({
        description: existing.data.description,
        settlementDate: existing.data.settlementDate.slice(0, 10),
        medicalSalesRepId: existing.data.medicalSalesRepId,
      });
      setLoaded(true);
    }
  }, [existing.data, loaded, setForm]);

  const save = useMutation({
    mutationFn: (f: SettlementForm) => {
      const header = { description: f.description.trim(), settlementDate: f.settlementDate, medicalSalesRepId: f.medicalSalesRepId! };
      return id ? settlementRepository.updateHeader({ ...header, id }) : settlementRepository.create(header);
    },
    onSuccess: (s) => {
      qc.setQueryData(["settlement", s.id], s);
      void qc.invalidateQueries({ queryKey: ["settlements"] });
    },
    onError: (e) => e instanceof ApiError && e.fields && setErrors(e.fields),
  });

  const locked = existing.data !== undefined && !canEditHeader(existing.data);
  return {
    isEdit: !!id,
    isLoading: !!id && existing.isPending,
    loadError: locked ? "Remove all invoices before editing this settlement." : (existing.error?.message ?? null),
    retryLoad: () => void existing.refetch(),
    form,
    errors,
    setField,
    saving: save.isPending,
    error: fieldAwareError(save.error),
    savedId: save.data?.id ?? null,
    submit() {
      const e: FieldErrors = {};
      if (!form.description.trim()) e.description = "Required";
      if (!form.settlementDate) e.settlementDate = "Required";
      if (!form.medicalSalesRepId) e.medicalSalesRepId = "Select a medical sales rep";
      setErrors(e);
      if (Object.keys(e).length === 0 && !save.isPending) save.mutate(form);
    },
  };
}

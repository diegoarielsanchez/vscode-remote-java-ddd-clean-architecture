import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { validateUpload } from "../../core/files";
import { formatDate, formatDateTime, isFutureLocal, toApiDateTime, toInputDateTime, toIsoDate, todayIso } from "../../core/format";
import { ApiError, type FieldErrors } from "../../core/http";
import { useFormState } from "../../core/useFormState";
import { useHcpDirectory } from "../hcp/useHcpDirectory";
import { useMsrDirectory } from "../msr/useMsrDirectory";
import { visitPlanRepository, visitRepository } from "./visitRepository";

const PAGE_SIZE = 20;
const nextPage = (last: unknown[], all: unknown[][]) => (last.length < PAGE_SIZE ? undefined : all.length + 1);

// ── Visit list ───────────────────────────────────────────────────────────────────

export function useVisitListViewModel() {
  const q = useInfiniteQuery({
    queryKey: ["visits"],
    queryFn: ({ pageParam, signal }) => visitRepository.list(pageParam, PAGE_SIZE, signal),
    initialPageParam: 1,
    getNextPageParam: nextPage,
  });
  const hcp = useHcpDirectory();
  const msr = useMsrDirectory();

  const rows = (q.data?.pages.flat() ?? []).map((v) => ({
    id: v.id,
    date: formatDate(v.visitDate),
    hcp: hcp.nameOf(v.healthCareProfId) ?? v.healthCareProfId ?? "Unknown HCP",
    details: [msr.nameOf(v.medicalSalesRepId), v.visitSiteId && `Site ${v.visitSiteId}`].filter(Boolean).join(" · "),
    files: v.productPromoAttachments.length,
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

// ── Visit detail ─────────────────────────────────────────────────────────────────

export function useVisitDetailViewModel(id: string) {
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ["visit", id], queryFn: ({ signal }) => visitRepository.get(id, signal) });
  const hcp = useHcpDirectory();
  const msr = useMsrDirectory();
  const [flash, setFlash] = useState<string | null>(null);

  const upload = useMutation({
    mutationFn: (files: File[]) => visitRepository.uploadAttachments(id, files),
    onSuccess: (r) => {
      setFlash(`Uploaded ${r.attachments.length} file(s).`);
      void qc.invalidateQueries({ queryKey: ["visit", id] });
      void qc.invalidateQueries({ queryKey: ["visits"] });
    },
    onError: (e) => setFlash(e.message),
  });

  const v = q.data;
  return {
    isLoading: q.isPending,
    error: q.error?.message ?? null,
    retry: () => void q.refetch(),
    visit: v && {
      id: v.id,
      date: formatDate(v.visitDate),
      hcp: hcp.nameOf(v.healthCareProfId) ?? v.healthCareProfId ?? "—",
      msr: msr.nameOf(v.medicalSalesRepId) ?? v.medicalSalesRepId ?? "—",
      site: v.visitSiteId ?? "—",
      comments: v.visitComments || "—",
      attachments: v.productPromoAttachments,
    },
    uploading: upload.isPending,
    upload(files: File[]) {
      const problem = files.map(validateUpload).find((p) => p !== null);
      if (problem) return setFlash(problem);
      if (files.length > 0) upload.mutate(files);
    },
    flash,
    clearFlash: () => setFlash(null),
  };
}

// ── Shared form plumbing ─────────────────────────────────────────────────────────

function useLoadOnce<T>(data: T | undefined, apply: (data: T) => void) {
  const [done, setDone] = useState(false);
  useEffect(() => {
    if (!done && data !== undefined) {
      apply(data);
      setDone(true);
    }
  }, [data, done, apply]);
}

function saveError(e: Error | null): string | null {
  if (!e) return null;
  return e instanceof ApiError && e.fields ? null : e.message; // field errors are shown inline
}

// ── Visit form ───────────────────────────────────────────────────────────────────

type VisitForm = {
  visitDate: string;
  healthCareProfId: string | null;
  medicalSalesRepId: string | null;
  visitSiteId: string;
  visitComments: string;
};

export function useVisitFormViewModel(id?: string) {
  const qc = useQueryClient();
  const existing = useQuery({
    queryKey: ["visit", id],
    queryFn: ({ signal }) => visitRepository.get(id!, signal),
    enabled: !!id,
  });
  const { form, setForm, errors, setErrors, setField } = useFormState<VisitForm>({
    visitDate: todayIso(),
    healthCareProfId: null,
    medicalSalesRepId: null,
    visitSiteId: "",
    visitComments: "",
  });

  useLoadOnce(existing.data, (v) =>
    setForm({
      visitDate: v.visitDate.slice(0, 10),
      healthCareProfId: v.healthCareProfId ?? null,
      medicalSalesRepId: v.medicalSalesRepId ?? null,
      visitSiteId: v.visitSiteId ?? "",
      visitComments: v.visitComments ?? "",
    }),
  );

  const save = useMutation({
    mutationFn: visitRepository.save,
    onSuccess: (v) => {
      qc.setQueryData(["visit", v.id], v);
      void qc.invalidateQueries({ queryKey: ["visits"] });
    },
    onError: (e) => e instanceof ApiError && e.fields && setErrors(e.fields),
  });

  return {
    isEdit: !!id,
    isLoading: !!id && existing.isPending,
    loadError: existing.error?.message ?? null,
    retryLoad: () => void existing.refetch(),
    form,
    errors,
    setField,
    saving: save.isPending,
    error: saveError(save.error),
    savedId: save.data?.id ?? null,
    submit() {
      const e: FieldErrors = {};
      if (!form.visitDate) e.visitDate = "Required";
      if (!form.healthCareProfId) e.healthCareProfId = "Select a healthcare professional";
      if (!form.medicalSalesRepId) e.medicalSalesRepId = "Select a medical sales rep";
      if (!form.visitSiteId.trim()) e.visitSiteId = "Required";
      setErrors(e);
      if (Object.keys(e).length > 0 || save.isPending) return;
      save.mutate({
        id,
        visitDate: form.visitDate,
        healthCareProfId: form.healthCareProfId!,
        medicalSalesRepId: form.medicalSalesRepId!,
        visitSiteId: form.visitSiteId.trim(),
        visitComments: form.visitComments,
      });
    },
  };
}

// ── Visit plan list ──────────────────────────────────────────────────────────────

export function useVisitPlanListViewModel() {
  const q = useInfiniteQuery({
    queryKey: ["visitPlans"],
    queryFn: ({ pageParam, signal }) => visitPlanRepository.list(pageParam, PAGE_SIZE, signal),
    initialPageParam: 1,
    getNextPageParam: nextPage,
  });
  const hcp = useHcpDirectory();
  const msr = useMsrDirectory();

  const rows = (q.data?.pages.flat() ?? []).map((p) => ({
    id: p.id,
    when: formatDateTime(p.visitDateTime),
    hcp: hcp.nameOf(p.healthCareProfId) ?? p.healthCareProfId ?? "Unknown HCP",
    details: [msr.nameOf(p.medicalSalesRepId), p.visitSiteId && `Site ${p.visitSiteId}`].filter(Boolean).join(" · "),
    active: p.active,
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

// ── Visit plan form ──────────────────────────────────────────────────────────────

type VisitPlanForm = {
  visitDateTime: string; // datetime-local value
  healthCareProfId: string | null;
  medicalSalesRepId: string | null;
  visitSiteId: string;
  visitComments: string;
};

const tomorrowAtNine = () => {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return `${toIsoDate(d)}T09:00`;
};

export function useVisitPlanFormViewModel(id?: string) {
  const qc = useQueryClient();
  const existing = useQuery({
    queryKey: ["visitPlan", id],
    queryFn: ({ signal }) => visitPlanRepository.get(id!, signal),
    enabled: !!id,
  });
  const { form, setForm, errors, setErrors, setField } = useFormState<VisitPlanForm>({
    visitDateTime: tomorrowAtNine(),
    healthCareProfId: null,
    medicalSalesRepId: null,
    visitSiteId: "",
    visitComments: "",
  });

  useLoadOnce(existing.data, (p) =>
    setForm({
      visitDateTime: toInputDateTime(p.visitDateTime),
      healthCareProfId: p.healthCareProfId ?? null,
      medicalSalesRepId: p.medicalSalesRepId ?? null,
      visitSiteId: p.visitSiteId ?? "",
      visitComments: p.visitComments ?? "",
    }),
  );

  const save = useMutation({
    mutationFn: visitPlanRepository.save,
    onSuccess: (p) => {
      qc.setQueryData(["visitPlan", p.id], p);
      void qc.invalidateQueries({ queryKey: ["visitPlans"] });
    },
    onError: (e) => e instanceof ApiError && e.fields && setErrors(e.fields),
  });

  return {
    isEdit: !!id,
    active: existing.data?.active ?? true,
    isLoading: !!id && existing.isPending,
    loadError: existing.error?.message ?? null,
    retryLoad: () => void existing.refetch(),
    form,
    errors,
    setField,
    saving: save.isPending,
    error: saveError(save.error),
    saved: save.isSuccess,
    submit() {
      const e: FieldErrors = {};
      // Backend has @Future on visitDateTime (server clock, zone-less).
      if (!isFutureLocal(form.visitDateTime)) e.visitDateTime = "Must be in the future";
      if (!form.healthCareProfId) e.healthCareProfId = "Select a healthcare professional";
      if (!form.medicalSalesRepId) e.medicalSalesRepId = "Select a medical sales rep";
      if (!form.visitSiteId.trim()) e.visitSiteId = "Required";
      setErrors(e);
      if (Object.keys(e).length > 0 || save.isPending) return;
      save.mutate({
        id,
        visitDateTime: toApiDateTime(form.visitDateTime),
        healthCareProfId: form.healthCareProfId!,
        medicalSalesRepId: form.medicalSalesRepId!,
        visitSiteId: form.visitSiteId.trim(),
        visitComments: form.visitComments,
      });
    },
  };
}

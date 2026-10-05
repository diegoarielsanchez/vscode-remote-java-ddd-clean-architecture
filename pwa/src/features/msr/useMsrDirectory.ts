import { useQuery } from "@tanstack/react-query";
import { useCallback, useMemo } from "react";
import type { PickerItem } from "../../core/ui/Picker";
import { msrRepository, toMsrPickerItem } from "./msrRepository";

/** Session-cached MSR directory: picker items (active only) and id → name for lists. */
export function useMsrDirectory() {
  const q = useQuery({
    queryKey: ["directory", "msr"],
    queryFn: ({ signal }) => msrRepository.all(signal),
    staleTime: Infinity,
  });

  const byId = useMemo(() => new Map((q.data ?? []).map((m) => [m.id, m])), [q.data]);
  const pickerItems = useMemo(
    () =>
      (q.data ?? [])
        .filter((m) => m.active)
        .sort((a, b) => a.fullName.localeCompare(b.fullName))
        .map(toMsrPickerItem),
    [q.data],
  );

  const nameOf = useCallback((id?: string | null) => (id ? byId.get(id)?.fullName : undefined), [byId]);
  const itemFor = useCallback(
    (id?: string | null): PickerItem | null => {
      if (!id) return null;
      const m = byId.get(id);
      return m ? toMsrPickerItem(m) : { id, title: id };
    },
    [byId],
  );

  return {
    isLoading: q.isPending,
    error: q.error?.message ?? null,
    retry: () => void q.refetch(),
    pickerItems,
    nameOf,
    itemFor,
  };
}

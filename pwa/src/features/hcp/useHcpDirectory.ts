import { useQuery } from "@tanstack/react-query";
import { useCallback, useMemo } from "react";
import type { PickerItem } from "../../core/ui/Picker";
import { hcpRepository, toHcpPickerItem } from "./hcpRepository";

/** Session-cached HCP directory: picker items (active only) and id → name for lists. */
export function useHcpDirectory() {
  const q = useQuery({
    queryKey: ["directory", "hcp"],
    queryFn: ({ signal }) => hcpRepository.all(signal),
    staleTime: Infinity,
  });

  const byId = useMemo(() => new Map((q.data ?? []).map((h) => [h.id, h])), [q.data]);
  const pickerItems = useMemo(
    () =>
      (q.data ?? [])
        .filter((h) => h.active)
        .sort((a, b) => a.fullName.localeCompare(b.fullName))
        .map(toHcpPickerItem),
    [q.data],
  );

  const nameOf = useCallback((id?: string | null) => (id ? byId.get(id)?.fullName : undefined), [byId]);
  const itemFor = useCallback(
    (id?: string | null): PickerItem | null => {
      if (!id) return null;
      const h = byId.get(id);
      return h ? toHcpPickerItem(h) : { id, title: id };
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

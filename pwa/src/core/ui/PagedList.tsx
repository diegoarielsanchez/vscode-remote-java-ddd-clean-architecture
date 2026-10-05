import { useEffect, useRef, type ReactNode } from "react";
import { EmptyState, ErrorBox, Spinner } from "./components";

type Props = {
  isLoading: boolean;
  error: string | null;
  isEmpty: boolean;
  emptyText: string;
  hasMore: boolean;
  isFetchingMore: boolean;
  onLoadMore: () => void;
  onRetry: () => void;
  children: ReactNode;
};

/**
 * Infinite list for the backend `POST .../list?page=N&pageSize=M` endpoints. Loads the next page
 * when the sentinel scrolls into view, with a button as the keyboard/screen-reader fallback.
 */
export function PagedList(p: Props) {
  const sentinel = useRef<HTMLDivElement>(null);
  const { hasMore, isFetchingMore, onLoadMore } = p;

  useEffect(() => {
    const el = sentinel.current;
    if (!el || !hasMore || !("IntersectionObserver" in window)) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting && !isFetchingMore) onLoadMore();
    }, { rootMargin: "200px" });
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasMore, isFetchingMore, onLoadMore]);

  if (p.isLoading) return <Spinner />;
  if (p.error && p.isEmpty) return <ErrorBox message={p.error} onRetry={p.onRetry} />;
  if (p.isEmpty) return <EmptyState>{p.emptyText}</EmptyState>;

  return (
    <div className="pb-24">
      <ul className="divide-y divide-slate-200 bg-white">{p.children}</ul>
      {p.error && <ErrorBox message={p.error} onRetry={p.onRetry} />}
      <div ref={sentinel} className="flex justify-center p-4">
        {isFetchingMore ? (
          <Spinner label="Loading more…" />
        ) : (
          hasMore && (
            <button type="button" className="btn-secondary" onClick={onLoadMore}>
              Load more
            </button>
          )
        )}
      </div>
    </div>
  );
}

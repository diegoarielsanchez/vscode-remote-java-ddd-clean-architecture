import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { ErrorBox, Modal, Spinner } from "./components";

/** A selectable entry: an HCP, an MSR, ... */
export type PickerItem = { id: string; title: string; subtitle?: string; tags?: string[] };

export function filterItems(items: PickerItem[], query: string, tag: string | null): PickerItem[] {
  const needle = query.trim().toLowerCase();
  return items.filter(
    (item) =>
      (tag === null || (item.tags ?? []).includes(tag)) &&
      (needle === "" ||
        item.title.toLowerCase().includes(needle) ||
        (item.subtitle ?? "").toLowerCase().includes(needle) ||
        (item.tags ?? []).some((t) => t.toLowerCase().includes(needle))),
  );
}

type PickerProps = {
  label: string;
  value: PickerItem | null;
  onChange: (item: PickerItem) => void;
  items: PickerItem[];
  isLoading: boolean;
  loadError: string | null;
  onRetry: () => void;
  error?: string;
};

/**
 * Searchable picker ("seeker"): a field showing the selection that opens a dialog with an
 * ARIA combobox + listbox. Filtering happens on the device (see the HCP/MSR directories).
 */
export function Picker(p: PickerProps) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const id = useId();

  const close = () => {
    setOpen(false);
    trigger.current?.focus();
  };

  return (
    <div>
      <label htmlFor={id} className="label">
        {p.label}
      </label>
      <button
        id={id}
        ref={trigger}
        type="button"
        aria-haspopup="dialog"
        aria-invalid={!!p.error}
        aria-describedby={p.error ? `${id}-error` : undefined}
        className={`field flex items-center justify-between ${p.error ? "field-invalid" : ""}`}
        onClick={() => setOpen(true)}
      >
        <span className={p.value ? "" : "text-slate-400"}>{p.value?.title ?? "Tap to search"}</span>
        <span aria-hidden className="text-slate-400">
          ⌕
        </span>
      </button>
      {p.error && (
        <p id={`${id}-error`} className="field-error">
          {p.error}
        </p>
      )}
      {open && (
        <PickerDialog
          {...p}
          onClose={close}
          onPick={(item) => {
            p.onChange(item);
            close();
          }}
        />
      )}
    </div>
  );
}

function PickerDialog({
  label,
  items,
  isLoading,
  loadError,
  onRetry,
  onPick,
  onClose,
}: PickerProps & { onPick: (item: PickerItem) => void; onClose: () => void }) {
  const [query, setQuery] = useState("");
  const [tag, setTag] = useState<string | null>(null);
  const [active, setActive] = useState(0);
  const listId = useId();
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => input.current?.focus(), []);

  const tags = useMemo(() => [...new Set(items.flatMap((i) => i.tags ?? []))].sort(), [items]);
  const results = useMemo(() => filterItems(items, query, tag), [items, query, tag]);
  useEffect(() => setActive(0), [query, tag]);

  const optionId = (item: PickerItem) => `${listId}-${item.id}`;

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setActive((a) => Math.min(a + 1, results.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (results[active]) onPick(results[active]);
    }
  };

  return (
    <Modal title={`Select ${label.toLowerCase()}`} onClose={onClose}>
      <input
        ref={input}
        type="search"
        role="combobox"
        aria-label={`Search ${label.toLowerCase()}`}
        aria-expanded="true"
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={results[active] ? optionId(results[active]) : undefined}
        placeholder="Search by name, email…"
        maxLength={100}
        autoComplete="off"
        className="field"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={onKeyDown}
      />

      {tags.length > 0 && (
        <div className="mt-3 flex gap-2 overflow-x-auto pb-1" aria-label="Filter by specialty">
          {tags.map((t) => (
            <button
              key={t}
              type="button"
              aria-pressed={tag === t}
              className={`whitespace-nowrap rounded-full border px-3 py-1 text-sm ${
                tag === t ? "border-brand bg-brand text-white" : "border-slate-300 bg-white"
              }`}
              onClick={() => setTag((cur) => (cur === t ? null : t))}
            >
              {t}
            </button>
          ))}
        </div>
      )}

      <div className="mt-3 min-h-[12rem]">
        {isLoading ? (
          <Spinner />
        ) : loadError ? (
          <ErrorBox message={loadError} onRetry={onRetry} />
        ) : results.length === 0 ? (
          <p className="p-6 text-center text-slate-500">No matches.</p>
        ) : (
          <ul id={listId} role="listbox" aria-label={label} className="divide-y divide-slate-100">
            {results.map((item, i) => (
              <li
                key={item.id}
                id={optionId(item)}
                role="option"
                aria-selected={i === active}
                className={`cursor-pointer px-3 py-2 ${i === active ? "bg-brand-light" : ""}`}
                onMouseEnter={() => setActive(i)}
                onClick={() => onPick(item)}
              >
                <p className="font-medium">{item.title}</p>
                {(item.subtitle || item.tags?.length) && (
                  <p className="text-sm text-slate-500">
                    {[item.subtitle, item.tags?.join(", ")].filter(Boolean).join(" · ")}
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  );
}

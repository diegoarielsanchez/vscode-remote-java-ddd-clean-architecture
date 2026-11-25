// A fixed locale: server and browser must render the same text (hydration).
const LOCALE = "en-GB";

/** Backend dates are zone-less LocalDate/LocalDateTime ("2026-01-31T10:00:00"); Instants end in Z. */
export function formatDate(value?: string | null): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(value ?? "");
  if (!m) return "—";
  const d = new Date(Date.UTC(+m[1]!, +m[2]! - 1, +m[3]!));
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: "medium", timeZone: "UTC" }).format(d);
}

export function formatDateTime(value?: string | null): string {
  const t = /T(\d{2}:\d{2})/.exec(value ?? "");
  if (!t) return formatDate(value);
  return `${formatDate(value)}, ${t[1]}${value!.endsWith("Z") ? " UTC" : ""}`;
}

export function formatMoney(value?: string | null): string {
  if (value == null || value === "") return "—";
  const n = Number(value);
  return Number.isFinite(n)
    ? new Intl.NumberFormat(LOCALE, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)
    : value;
}

/** DELIVERY_QUARANTINED → "Delivery quarantined" */
export const humanize = (code?: string | null) =>
  code ? code.charAt(0) + code.slice(1).toLowerCase().replaceAll("_", " ") : "—";

export const PAGE_SIZE = 10;
export const pageNumber = (value: string | string[] | undefined) =>
  typeof value === "string" && /^[1-9]\d{0,5}$/.test(value) ? Number(value) : 1;
export const slicePage = <T,>(items: T[], page: number) => ({
  items: items.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE),
  hasNext: items.length > page * PAGE_SIZE,
});

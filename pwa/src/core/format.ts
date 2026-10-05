// Backend dates are zone-less Java LocalDate / LocalDateTime:
//   requests  "yyyy-MM-dd" and "yyyy-MM-dd'T'HH:mm:ss"
//   responses ISO local date / date-time
// They are parsed as *local* time, never through Date.parse (which would apply UTC rules).

const DATE = /^(\d{4})-(\d{2})-(\d{2})/;
const TIME = /T(\d{2}):(\d{2})/;
const pad = (n: number) => String(n).padStart(2, "0");

export function parseLocal(value?: string | null): Date | null {
  const d = value ? DATE.exec(value) : null;
  if (!d) return null;
  const t = TIME.exec(value!);
  return new Date(+d[1], +d[2] - 1, +d[3], t ? +t[1] : 0, t ? +t[2] : 0);
}

export const toIsoDate = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const todayIso = () => toIsoDate(new Date());

/** `<input type="datetime-local">` value ("yyyy-MM-ddTHH:mm") → backend "yyyy-MM-ddTHH:mm:ss". */
export const toApiDateTime = (local: string) => (local.length === 16 ? `${local}:00` : local.slice(0, 19));

/** Backend date-time → `<input type="datetime-local">` value. */
export const toInputDateTime = (value?: string | null) => (value ? value.slice(0, 16) : "");

export const isFutureLocal = (local: string) => {
  const d = parseLocal(local);
  return d !== null && d.getTime() > Date.now();
};

export function formatDate(value?: string | null): string {
  const d = parseLocal(value);
  return d ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(d) : "—";
}

export function formatDateTime(value?: string | null): string {
  const d = parseLocal(value);
  return d ? new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(d) : "—";
}

/** Display only: amounts stay strings everywhere else (Java BigDecimal). */
export function formatMoney(value?: string | null): string {
  if (value == null || value === "") return "—";
  const n = Number(value);
  return Number.isFinite(n)
    ? new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)
    : value;
}

export const AMOUNT_PATTERN = /^\d{1,13}(\.\d{1,2})?$/;

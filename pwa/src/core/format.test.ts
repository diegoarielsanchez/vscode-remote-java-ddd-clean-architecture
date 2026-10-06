import { describe, expect, it } from "vitest";
import { AMOUNT_PATTERN, parseLocal, toApiDateTime, toInputDateTime } from "./format";
import { validateUpload } from "./files";

describe("dates", () => {
  it("parses zone-less backend values as local time", () => {
    const d = parseLocal("2026-10-05T14:30:00")!;
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()]).toEqual([2026, 9, 5, 14, 30]);
    expect(parseLocal("2026-10-05")!.getHours()).toBe(0);
    expect(parseLocal("garbage")).toBeNull();
  });

  it("converts between datetime-local and the backend format", () => {
    expect(toApiDateTime("2026-10-05T14:30")).toBe("2026-10-05T14:30:00");
    expect(toInputDateTime("2026-10-05T14:30:00")).toBe("2026-10-05T14:30");
  });
});

describe("validation", () => {
  it("accepts decimal amounts with up to two decimals", () => {
    expect(AMOUNT_PATTERN.test("1250.50")).toBe(true);
    expect(AMOUNT_PATTERN.test("-1")).toBe(false);
    expect(AMOUNT_PATTERN.test("1.234")).toBe(false);
  });

  it("checks upload type and size like the backend", () => {
    expect(validateUpload(new File(["x"], "invoice.PDF"))).toBeNull();
    expect(validateUpload(new File(["x"], "script.html"))).toMatch(/unsupported/);
    expect(validateUpload(new File([], "empty.pdf"))).toMatch(/empty/);
  });
});

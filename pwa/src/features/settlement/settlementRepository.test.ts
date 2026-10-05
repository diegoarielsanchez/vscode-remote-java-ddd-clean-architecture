// @vitest-environment node
import { http as mock, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { server } from "../../test/server";
import { settlementRepository } from "./settlementRepository";

const settlement = {
  id: "s1",
  description: "Q3",
  settlementDate: "2026-09-30",
  status: "DRAFT",
  totalAmount: 1234.56,
  invoices: [{ id: "i1", invoiceNumber: "A-1", issueDate: "2026-09-01", dueDate: null, amount: 1234.56, fileName: "a.pdf", sizeInBytes: 2048 }],
  medicalSalesRepId: "m1",
};

describe("settlementRepository", () => {
  it("keeps BigDecimal amounts as strings", async () => {
    server.use(mock.get("*/api/v1/settlement/s1", () => HttpResponse.json(settlement)));
    const s = await settlementRepository.get("s1");
    expect(s.totalAmount).toBe("1234.56");
    expect(s.invoices[0].amount).toBe("1234.56");
  });

  it("sends an invoice as multipart form fields plus a file part", async () => {
    let fields: Record<string, string> = {};
    let file: File | null = null;
    server.use(
      mock.post("*/api/v1/settlement/invoice/add", async ({ request }) => {
        const form = await request.formData();
        fields = Object.fromEntries([...form.entries()].filter(([, v]) => typeof v === "string")) as Record<string, string>;
        file = form.get("file") as File;
        return HttpResponse.json({ id: "i9", invoiceNumber: "B-7", issueDate: "2026-10-01", amount: 99.9 }, { status: 201 });
      }),
    );
    await settlementRepository.addInvoice("s1", {
      invoiceNumber: "B-7",
      issueDate: "2026-10-01",
      amount: "99.90",
      file: new File(["%PDF"], "inv.pdf", { type: "application/pdf" }),
    });
    expect(fields).toEqual({ settlementId: "s1", invoiceNumber: "B-7", issueDate: "2026-10-01", amount: "99.90" });
    expect(file!.name).toBe("inv.pdf");
  });

  it("removes an invoice with a DELETE carrying a JSON body", async () => {
    let body: unknown;
    server.use(
      mock.delete("*/api/v1/settlement/invoice/remove", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...settlement, invoices: [] });
      }),
    );
    await settlementRepository.removeInvoice("s1", "i1");
    expect(body).toEqual({ settlementId: "s1", invoiceId: "i1" });
  });

  it("refuses to update the header of a settlement that has invoices", async () => {
    let putCalled = false;
    server.use(
      mock.get("*/api/v1/settlement/s1", () => HttpResponse.json(settlement)),
      mock.put("*/api/v1/settlement/update", () => {
        putCalled = true;
        return HttpResponse.json(settlement);
      }),
    );
    await expect(
      settlementRepository.updateHeader({ id: "s1", description: "x", settlementDate: "2026-09-30", medicalSalesRepId: "m1" }),
    ).rejects.toMatchObject({ status: 409 });
    expect(putCalled).toBe(false);
  });

  it("creates a settlement with an explicit empty invoice list", async () => {
    let body: unknown;
    server.use(
      mock.post("*/api/v1/settlement/create", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...settlement, id: "s2", invoices: [] }, { status: 201 });
      }),
    );
    await settlementRepository.create({ description: "New", settlementDate: "2026-10-05", medicalSalesRepId: "m1" });
    expect(body).toEqual({ description: "New", settlementDate: "2026-10-05", medicalSalesRepId: "m1", invoices: [] });
  });
});

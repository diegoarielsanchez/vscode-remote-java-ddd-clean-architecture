import { screen } from "@testing-library/react";
import { http as mock, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { renderView } from "../../test/render";
import { server } from "../../test/server";
import { VisitListView } from "./VisitViews";

const directories = [
  mock.post("*/api/v1/healthcareprof/list", () =>
    HttpResponse.json([{ id: "h1", name: "Ana", surname: "Gómez", email: "ana@clinic.org", active: true, specialties: [] }]),
  ),
  mock.post("*/api/v1/medicalsalesrep/list", () =>
    HttpResponse.json([{ id: "m1", name: "Rita", surname: "Paz", email: "rita@pharma.com", active: true }]),
  ),
];

describe("VisitListView", () => {
  it("shows visits with names resolved from the HCP and MSR directories", async () => {
    server.use(
      ...directories,
      mock.post("*/api/v1/visit/list", () =>
        HttpResponse.json([
          { id: "v1", visitDate: "2026-10-01T00:00:00", healthCareProfId: "h1", medicalSalesRepId: "m1", visitSiteId: "S-9", productPromoAttachments: [] },
        ]),
      ),
    );
    renderView(<VisitListView />);
    expect(await screen.findByText("Ana Gómez")).toBeInTheDocument();
    expect(await screen.findByText("Rita Paz · Site S-9")).toBeInTheDocument();
  });

  it("renders text from the server as text, not HTML", async () => {
    server.use(
      ...directories,
      mock.post("*/api/v1/visit/list", () =>
        HttpResponse.json([{ id: "v2", visitDate: "2026-10-01T00:00:00", healthCareProfId: "<img src=x onerror=alert(1)>", productPromoAttachments: [] }]),
      ),
    );
    const { container } = renderView(<VisitListView />);
    expect(await screen.findByText("<img src=x onerror=alert(1)>")).toBeInTheDocument();
    expect(container.querySelector("img")).toBeNull();
  });

  it("shows the empty state when the backend answers 'not found'", async () => {
    server.use(
      ...directories,
      mock.post("*/api/v1/visit/list", () => HttpResponse.json({ statusCode: 400, message: "Visit not found." }, { status: 400 })),
    );
    renderView(<VisitListView />);
    expect(await screen.findByText("No visits yet.")).toBeInTheDocument();
  });
});

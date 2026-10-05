import { Navigate, Outlet, type RouteObject } from "react-router-dom";
import { useSession } from "../core/session";
import { EmptyState } from "../core/ui/components";
import { LoginView } from "../features/auth/LoginView";
import { SettlementDetailView, SettlementFormView, SettlementListView } from "../features/settlement/SettlementViews";
import {
  VisitDetailView,
  VisitFormView,
  VisitListView,
  VisitPlanFormView,
  VisitPlanListView,
} from "../features/visit/VisitViews";
import { AppShell } from "./AppShell";

/** Logged out → login screen in place, so the user returns to the same URL after signing in. */
function SessionGate() {
  return useSession() ? <Outlet /> : <LoginView />;
}

export const routes: RouteObject[] = [
  {
    element: <SessionGate />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/visits" replace /> },
          { path: "visits", element: <VisitListView /> },
          { path: "visits/new", element: <VisitFormView /> },
          { path: "visits/:id", element: <VisitDetailView /> },
          { path: "visits/:id/edit", element: <VisitFormView /> },
          { path: "plans", element: <VisitPlanListView /> },
          { path: "plans/new", element: <VisitPlanFormView /> },
          { path: "plans/:id", element: <VisitPlanFormView /> },
          { path: "settlements", element: <SettlementListView /> },
          { path: "settlements/new", element: <SettlementFormView /> },
          { path: "settlements/:id", element: <SettlementDetailView /> },
          { path: "settlements/:id/edit", element: <SettlementFormView /> },
          { path: "*", element: <EmptyState>Page not found.</EmptyState> },
        ],
      },
    ],
  },
];

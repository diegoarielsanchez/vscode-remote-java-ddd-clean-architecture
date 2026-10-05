import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider, createBrowserRouter } from "react-router-dom";
import { OfflineBanner } from "./OfflineBanner";
import { queryClient } from "./queryClient";
import { routes } from "./routes";
import { UpdatePrompt } from "./UpdatePrompt";

const router = createBrowserRouter(routes);

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <OfflineBanner />
      <RouterProvider router={router} />
      <UpdatePrompt />
    </QueryClientProvider>
  );
}
